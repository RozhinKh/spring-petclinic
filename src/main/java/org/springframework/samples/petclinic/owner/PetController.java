/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Wick Dynex
 */
@Controller
@RequestMapping("/owners/{ownerId}")
class PetController {

	private static final String VIEWS_PETS_CREATE_OR_UPDATE_FORM = "pets/createOrUpdatePetForm";

	private final OwnerRepository owners;

	private final PetTypeRepository types;

	public PetController(OwnerRepository owners, PetTypeRepository types) {
		this.owners = owners;
		this.types = types;
	}

	@ModelAttribute("types")
	public Collection<PetType> populatePetTypes() {
		// VIRTUALIZATION POINT (6/21): I/O-bound JPA operation - findPetTypes
		// File: PetController.java, Line: 63
		// Type: Database query (reference data fetch)
		// Involves: SQL SELECT query from PetType table
		// Virtual thread benefit: Lightweight execution for reference data lookups
		return this.types.findPetTypes();
	}

	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable("ownerId") int ownerId) {
		// VIRTUALIZATION POINT (7/21): I/O-bound JPA operation - findById
		// File: PetController.java, Line: 68
		// Type: Database query (parent entity fetch)
		// Involves: SQL SELECT with potential lazy loading of pets collection
		// Virtual thread benefit: Handles blocking query execution without platform thread exhaustion
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner;
	}

	@ModelAttribute("pet")
	public Pet findPet(@PathVariable("ownerId") int ownerId,
			@PathVariable(name = "petId", required = false) Integer petId) {

		if (petId == null) {
			return new Pet();
		}

		// VIRTUALIZATION POINT (8/21): I/O-bound JPA operation - findById (parent lookup)
		// File: PetController.java, Line: 82
		// Type: Database query (entity fetch for pet navigation)
		// Involves: SQL SELECT to load owner with pets, then collection lookup
		// Virtual thread benefit: Efficient handling of collection iteration and lookup
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner.getPet(petId);
	}

	@InitBinder("owner")
	public void initOwnerBinder(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	@InitBinder("pet")
	public void initPetBinder(WebDataBinder dataBinder) {
		dataBinder.setValidator(new PetValidator());
	}

	@GetMapping("/pets/new")
	public String initCreationForm(Owner owner, ModelMap model) {
		Pet pet = new Pet();
		owner.addPet(pet);
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/pets/new")
	public String processCreationForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		// Pattern matching: check if pet name exists and is new
		if (StringUtils.hasText(pet.name()) && pet.isNew() && owner.getPet(pet.name(), true) != null) {
			result.rejectValue("name", "duplicate", "already exists");
		}

		LocalDate currentDate = LocalDate.now();
		// Use switch expression to validate birth date
		boolean isValidBirthDate = switch (pet.birthDate()) {
			case null -> true;
			case LocalDate bd when bd.isAfter(currentDate) -> {
				result.rejectValue("birthDate", "typeMismatch.birthDate");
				yield false;
			}
			case LocalDate bd -> true;
		};

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		owner.addPet(pet);
		// VIRTUALIZATION POINT (9/21): I/O-bound JPA operation - save (pet creation)
		// File: PetController.java, Line: 130
		// Type: Database mutation (nested entity persist in transaction)
		// Involves: SQL INSERT for pet, potential owner update, transaction overhead
		// Virtual thread benefit: Non-blocking persistence of related entities
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Pet has been Added");
		return "redirect:/owners/{ownerId}";
	}

	@GetMapping("/pets/{petId}/edit")
	public String initUpdateForm() {
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/pets/{petId}/edit")
	public String processUpdateForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		String petName = pet.name();

		// Pattern matching with records: checking if the pet name already exists
		if (StringUtils.hasText(petName)) {
			Pet existingPet = owner.getPet(petName, false);
			// Use pattern matching to validate existing pet
			if (existingPet != null && !Objects.equals(existingPet.id(), pet.id())) {
				result.rejectValue("name", "duplicate", "already exists");
			}
		}

		LocalDate currentDate = LocalDate.now();
		// Switch expression for birth date validation
		boolean isValidBirthDate = switch (pet.birthDate()) {
			case null -> true;
			case LocalDate bd when bd.isAfter(currentDate) -> {
				result.rejectValue("birthDate", "typeMismatch.birthDate");
				yield false;
			}
			case LocalDate bd -> true;
		};

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		updatePetDetails(owner, pet);
		redirectAttributes.addFlashAttribute("message", "Pet details has been edited");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * VIRTUALIZATION POINTS (10/21 and 11/21): I/O-bound JPA operations in nested method
	 * File: PetController.java, Lines: 180-197 (updatePetDetails method)
	 * Contains two virtualization points:
	 * 1. owner.getPet(petId) - Line 183: Memory lookup but preceded by DB query
	 * 2. this.owners.save(owner) - Line 197: Database mutation
	 */

	/**
	 * Updates the pet details if it exists or adds a new pet to the owner.
	 * @param owner The owner of the pet
	 * @param pet The pet with updated details
	 */
	private void updatePetDetails(Owner owner, Pet pet) {
		Integer petId = pet.id();
		Assert.state(petId != null, "'pet.id()' must not be null");
		Pet existingPet = owner.getPet(petId);
		
		// Pattern matching with switch expression
		switch (existingPet) {
			case Pet ep when ep != null -> {
				// Update existing pet's properties by creating new record with updated fields
				Pet updatedPet = new Pet(ep.id(), pet.name(), pet.birthDate(), pet.type(), ep.visits());
				owner.pets().set(owner.pets().indexOf(ep), updatedPet);
			}
			case null -> {
				// Add new pet
				owner.addPet(pet);
			}
		}
		// VIRTUALIZATION POINT (10/21): I/O-bound JPA operation - save (pet update)
		// File: PetController.java, Line: 197
		// Type: Database mutation (nested entity merge in transaction)
		// Involves: SQL UPDATE for pet, cascading updates, transaction overhead
		// Virtual thread benefit: Lightweight concurrent updates to pet records
		this.owners.save(owner);
	}

}
