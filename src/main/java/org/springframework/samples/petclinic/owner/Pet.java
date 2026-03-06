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
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * Simple business object representing a pet.
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Wick Dynex
 */
@Entity
@Table(name = "pets")
@Access(AccessType.FIELD)
public class Pet extends BaseEntity {

	@Column
	@NotBlank
	private String name;

	@Column
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate birthDate;

	@ManyToOne
	@JoinColumn(name = "type_id")
	private PetType type;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "pet_id")
	@OrderBy("date ASC")
	private Set<Visit> visits = new LinkedHashSet<>();

	public Pet() {
	}

	public Pet(Integer id, String name, LocalDate birthDate, PetType type) {
		setId(id);
		this.name = name;
		this.birthDate = birthDate;
		this.type = type;
	}

	public Pet(Integer id, String name, LocalDate birthDate, PetType type, Set<Visit> visits) {
		setId(id);
		this.name = name;
		this.birthDate = birthDate;
		this.type = type;
		if (visits != null) {
			this.visits = visits;
		}
	}

	public Pet(String name, LocalDate birthDate, PetType type) {
		this.name = name;
		this.birthDate = birthDate;
		this.type = type;
	}

	public Pet(String name) {
		this.name = name;
	}

	/** Record-style accessor. */
	public Integer id() {
		return getId();
	}

	/** Record-style accessor. */
	public String name() {
		return name;
	}

	/** Record-style accessor. */
	public LocalDate birthDate() {
		return birthDate;
	}

	/** Record-style accessor. */
	public PetType type() {
		return type;
	}

	/** Record-style accessor. */
	public Set<Visit> visits() {
		return visits;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public void setBirthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
	}

	public PetType getType() {
		return type;
	}

	public void setType(PetType type) {
		this.type = type;
	}

	public Collection<Visit> getVisits() {
		return visits;
	}

	public void addVisit(Visit visit) {
		if (visit != null) {
			visits.add(visit);
		}
	}

}
