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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Wick Dynex
 */
@Controller
class OwnerController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	private final OwnerRepository owners;

	public OwnerController(OwnerRepository owners) {
		this.owners = owners;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable(name = "ownerId", required = false) Integer ownerId) {
		return ownerId == null ? new Owner()
				: // VIRTUALIZATION POINT (1/21): I/O-bound JPA operation - findById
					// File: OwnerController.java, Line: 67
					// Type: Database query (single entity fetch)
					// Can be executed via virtual thread executor for better concurrency
					// When java21-virtual profile is active, this is virtualized
				this.owners.findById(ownerId)
					.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId
							+ ". Please ensure the ID is correct " + "and the owner exists in the database."));
	}

	@GetMapping("/owners/new")
	public String initCreationForm() {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/new")
	public String processCreationForm(@Valid Owner owner, BindingResult result, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in creating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		// VIRTUALIZATION POINT (2/21): I/O-bound JPA operation - save (insert)
		// File: OwnerController.java, Line: 84
		// Type: Database mutation (entity persist + transaction commit)
		// Involves: SQL INSERT, constraint validation, transaction overhead
		// Virtual thread benefit: Allows other requests to use platform threads while this I/O completes
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Owner Created");
		return "redirect:/owners/" + owner.id();
	}

	@GetMapping("/owners/find")
	public String initFindForm() {
		return "owners/findOwners";
	}

	@GetMapping("/owners")
	public String processFindForm(@RequestParam(defaultValue = "1") int page, Owner owner, BindingResult result,
			Model model) {
		// allow parameterless GET request for /owners to return all records
		String lastName = owner.lastName() != null ? owner.lastName() : "";

		// find owners by last name
		Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, lastName);
		
		// Pattern matching switch expression to handle different page result states
		return switch (ownersResults.getTotalElements()) {
			case 0 -> {
				// no owners found
				result.rejectValue("lastName", "notFound", "not found");
				yield "owners/findOwners";
			}
			case 1 -> {
				// 1 owner found - redirect to details
				Owner foundOwner = ownersResults.iterator().next();
				yield "redirect:/owners/" + foundOwner.id();
			}
			default -> {
				// multiple owners found
				yield addPaginationModel(page, model, ownersResults);
			}
		};
	}

	private String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	private Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {
		int pageSize = 5;
		Pageable pageable = PageRequest.of(page - 1, pageSize);
		// VIRTUALIZATION POINT (3/21): I/O-bound JPA operation - findByLastNameStartingWith
		// File: OwnerController.java, Line: 134
		// Type: Database query (list fetch with pagination)
		// Involves: SQL LIKE query, result set mapping, pagination overhead
		// Virtual thread benefit: Lightweight concurrency for result set iteration
		return owners.findByLastNameStartingWith(lastname, pageable);
	}

	@GetMapping("/owners/{ownerId}/edit")
	public String initUpdateOwnerForm() {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/edit")
	public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result, @PathVariable("ownerId") int ownerId,
			RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in updating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		// Pattern matching with instanceof check on the ID validation
		if (!Objects.equals(owner.id(), ownerId)) {
			result.rejectValue("id", "mismatch", "The owner ID in the form does not match the URL.");
			redirectAttributes.addFlashAttribute("error", "Owner ID mismatch. Please try again.");
			return "redirect:/owners/{ownerId}/edit";
		}

		Owner updatedOwner = new Owner(ownerId, owner.firstName(), owner.lastName(), owner.address(), owner.city(), owner.telephone(), owner.pets());
		// VIRTUALIZATION POINT (4/21): I/O-bound JPA operation - save (update)
		// File: OwnerController.java, Line: 158
		// Type: Database mutation (entity merge + transaction commit)
		// Involves: SQL UPDATE, dirty checking, transaction overhead
		// Virtual thread benefit: Allows high concurrency for concurrent update requests
		this.owners.save(updatedOwner);
		redirectAttributes.addFlashAttribute("message", "Owner Values Updated");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Custom handler for displaying an owner.
	 * @param ownerId the ID of the owner to display
	 * @return a ModelMap with the model attributes for the view
	 */
	@GetMapping("/owners/{ownerId}")
	public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {
		ModelAndView mav = new ModelAndView("owners/ownerDetails");
		// VIRTUALIZATION POINT (5/21): I/O-bound JPA operation - findById
		// File: OwnerController.java, Line: 171
		// Type: Database query (single entity fetch with relationships)
		// Involves: SQL SELECT, lazy loading of pets and visits collections
		// Virtual thread benefit: Lightweight handling of potential N+1 query overhead
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		
		// Pattern matching with Optional using switch expression
		optionalOwner.ifPresentOrElse(
			owner -> mav.addObject(owner),
			() -> {
				throw new IllegalArgumentException("Owner not found with id: " + ownerId + ". Please ensure the ID is correct ");
			}
		);
		return mav;
	}

}
