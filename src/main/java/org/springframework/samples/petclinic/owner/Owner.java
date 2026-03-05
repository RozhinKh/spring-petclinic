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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Simple JavaBean domain object representing an owner.
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Michael Isvy
 * @author Oliver Drotbohm
 * @author Wick Dynex
 */
@Entity
@Table(name = "owners")
public record Owner(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Integer id,
	@Column @NotBlank String firstName,
	@Column @NotBlank String lastName,
	@Column @NotBlank String address,
	@Column @NotBlank String city,
	@Column @NotBlank @Pattern(regexp = "\\d{10}", message = "{telephone.invalid}") String telephone,
	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER) @JoinColumn(name = "owner_id") @OrderBy("name") List<Pet> pets
) {

	public Owner(Integer id, String firstName, String lastName, String address, String city, String telephone) {
		this(id, firstName, lastName, address, city, telephone, new ArrayList<>());
	}

	public Owner(String firstName, String lastName) {
		this(null, firstName, lastName, null, null, null, new ArrayList<>());
	}

	public Owner() {
		this(null, null, null, null, null, null, new ArrayList<>());
	}

	public void addPet(Pet pet) {
		if (pet != null && pet.isNew() && pets != null) {
			pets.add(pet);
		}
	}

	public Pet getPet(String name) {
		return getPet(name, false);
	}

	public Pet getPet(Integer id) {
		if (pets == null) return null;
		for (Pet pet : pets) {
			if (!pet.isNew()) {
				Integer compId = pet.id();
				if (Objects.equals(compId, id)) {
					return pet;
				}
			}
		}
		return null;
	}

	public Pet getPet(String name, boolean ignoreNew) {
		if (pets == null) return null;
		for (Pet pet : pets) {
			String compName = pet.name();
			if (compName != null && compName.equalsIgnoreCase(name)) {
				if (!ignoreNew || !pet.isNew()) {
					return pet;
				}
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("id", this.id)
			.append("new", this.isNew())
			.append("lastName", this.lastName)
			.append("firstName", this.firstName)
			.append("address", this.address)
			.append("city", this.city)
			.append("telephone", this.telephone)
			.toString();
	}

	public void addVisit(Integer petId, Visit visit) {
		Assert.notNull(petId, "Pet identifier must not be null!");
		Assert.notNull(visit, "Visit must not be null!");

		Pet pet = getPet(petId);
		Assert.notNull(pet, "Invalid Pet identifier!");
		pet.addVisit(visit);
	}

	public boolean isNew() {
		return this.id == null;
	}
}
