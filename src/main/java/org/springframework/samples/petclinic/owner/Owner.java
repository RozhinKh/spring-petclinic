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
import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.util.Assert;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Access(AccessType.FIELD)
public class Owner extends BaseEntity {

	@Column
	@NotBlank
	private String firstName;

	@Column
	@NotBlank
	private String lastName;

	@Column
	@NotBlank
	private String address;

	@Column
	@NotBlank
	private String city;

	@Column
	@NotBlank
	@Pattern(regexp = "\\d{10}", message = "{telephone.invalid}")
	private String telephone;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "owner_id")
	@OrderBy("name")
	private List<Pet> pets = new ArrayList<>();

	public Owner() {
	}

	public Owner(Integer id, String firstName, String lastName, String address, String city, String telephone) {
		setId(id);
		this.firstName = firstName;
		this.lastName = lastName;
		this.address = address;
		this.city = city;
		this.telephone = telephone;
	}

	public Owner(Integer id, String firstName, String lastName, String address, String city, String telephone,
			List<Pet> pets) {
		this(id, firstName, lastName, address, city, telephone);
		if (pets != null) {
			this.pets = pets;
		}
	}

	public Owner(String firstName, String lastName) {
		this.firstName = firstName;
		this.lastName = lastName;
	}

	/** Record-style accessor. */
	public Integer id() {
		return getId();
	}

	/** Record-style accessor. */
	public String firstName() {
		return firstName;
	}

	/** Record-style accessor. */
	public String lastName() {
		return lastName;
	}

	/** Record-style accessor. */
	public String address() {
		return address;
	}

	/** Record-style accessor. */
	public String city() {
		return city;
	}

	/** Record-style accessor. */
	public String telephone() {
		return telephone;
	}

	/** Record-style accessor. */
	public List<Pet> pets() {
		return pets;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getTelephone() {
		return telephone;
	}

	public void setTelephone(String telephone) {
		this.telephone = telephone;
	}

	public List<Pet> getPets() {
		return pets;
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
		if (pets == null) {
			return null;
		}
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
		if (pets == null) {
			return null;
		}
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
		return new ToStringCreator(this).append("id", this.getId())
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

}
