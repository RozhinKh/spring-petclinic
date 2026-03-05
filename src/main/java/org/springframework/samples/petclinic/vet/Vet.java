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
package org.springframework.samples.petclinic.vet;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Simple JavaBean domain object representing a veterinarian.
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Arjen Poutsma
 */
@Entity
@Table(name = "vets")
public record Vet(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Integer id,
	@Column @NotBlank String firstName,
	@Column @NotBlank String lastName,
	@ManyToMany(fetch = FetchType.EAGER) @JoinTable(name = "vet_specialties", joinColumns = @JoinColumn(name = "vet_id"), inverseJoinColumns = @JoinColumn(name = "specialty_id")) Set<Specialty> specialties
) {

	public Vet(Integer id, String firstName, String lastName) {
		this(id, firstName, lastName, new HashSet<>());
	}

	public Vet(String firstName, String lastName) {
		this(null, firstName, lastName, new HashSet<>());
	}

	public Vet() {
		this(null, null, null, new HashSet<>());
	}

	protected Set<Specialty> getSpecialtiesInternal() {
		return (specialties != null) ? specialties : new HashSet<>();
	}

	@XmlElement
	public List<Specialty> getSpecialties() {
		return getSpecialtiesInternal().stream()
			.sorted(Comparator.comparing(Specialty::name))
			.collect(Collectors.toList());
	}

	public int getNrOfSpecialties() {
		return getSpecialtiesInternal().size();
	}

	public void addSpecialty(Specialty specialty) {
		getSpecialtiesInternal().add(specialty);
	}

	public boolean isNew() {
		return this.id == null;
	}
}
