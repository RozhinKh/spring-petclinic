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

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * Simple JavaBean domain object representing a visit.
 *
 * @author Ken Krebs
 * @author Dave Syer
 */
@Entity
@Table(name = "visits")
public record Visit(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Integer id,
	@Column(name = "visit_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
	@NotBlank String description
) {

	/**
	 * Creates a new instance of Visit for the current date
	 */
	public Visit(Integer id, String description) {
		this(id, LocalDate.now(), description);
	}

	public Visit(String description) {
		this(null, LocalDate.now(), description);
	}

	public Visit() {
		this(null, LocalDate.now(), null);
	}

	public boolean isNew() {
		return this.id == null;
	}
}
