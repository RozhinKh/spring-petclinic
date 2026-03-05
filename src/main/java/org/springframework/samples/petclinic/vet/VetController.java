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

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Ken Krebs
 * @author Arjen Poutsma
 */
@Controller
class VetController {

	private final VetRepository vetRepository;

	public VetController(VetRepository vetRepository) {
		this.vetRepository = vetRepository;
	}

	@GetMapping("/vets.html")
	public String showVetList(@RequestParam(defaultValue = "1") int page, Model model) {
		// Here we are returning an object of type 'Vets' rather than a collection of Vet
		// objects so it is simpler for Object-Xml mapping
		Vets vets = new Vets();
		// VIRTUALIZATION POINT (13/21): I/O-bound JPA operation - findAll paginated
		// File: VetController.java, Line: 49 (via findPaginated)
		// Type: Database query (list fetch with pagination and lazy loading)
		// Involves: SQL SELECT with LIMIT/OFFSET, loading vet specialties collection
		// Virtual thread benefit: Lightweight pagination and collection loading
		Page<Vet> paginated = findPaginated(page);
		
		// Pattern matching: if paginated has content, add it to the list
		if (!paginated.isEmpty()) {
			vets.getVetList().addAll(paginated.toList());
		}
		return addPaginationModel(page, paginated, model);
	}

	private String addPaginationModel(int page, Page<Vet> paginated, Model model) {
		List<Vet> listVets = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listVets", listVets);
		return "vets/vetList";
	}

	private Page<Vet> findPaginated(int page) {
		int pageSize = 5;
		Pageable pageable = PageRequest.of(page - 1, pageSize);
		// VIRTUALIZATION POINT (14/21): I/O-bound JPA operation - findAll(Pageable)
		// File: VetController.java, Line: 70
		// Type: Database query (paginated list with lazy-loaded specialties)
		// Involves: SQL SELECT with LIMIT/OFFSET, N+1 query risk for specialties
		// Virtual thread benefit: Handles pagination and eager/lazy loading efficiently
		return vetRepository.findAll(pageable);
	}

	@GetMapping({ "/vets" })
	public @ResponseBody Vets showResourcesVetList() {
		// Here we are returning an object of type 'Vets' rather than a collection of Vet
		// objects so it is simpler for JSon/Object mapping
		Vets vets = new Vets();
		// VIRTUALIZATION POINT (15/21): I/O-bound JPA operation - findAll (full list)
		// File: VetController.java, Line: 78
		// Type: Database query (complete table scan with @Cacheable annotation)
		// Involves: SQL SELECT all vets, loading specialties via lazy loading, caching overhead
		// Virtual thread benefit: Handles initial query execution and result set iteration
		// Note: @Cacheable("vets") caches results, so actual DB access is infrequent
		vets.getVetList().addAll(this.vetRepository.findAll());
		return vets;
	}

}
