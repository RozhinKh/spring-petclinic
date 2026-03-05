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
package org.springframework.samples.petclinic.benchmark;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.owner.PetTypeRepository;

/**
 * Generates test data for load testing PetClinic application.
 * Creates owners and pets programmatically to supplement SQL-based test data.
 *
 * @author Load Test Framework
 */
public class LoadTestDataGenerator {

	private final OwnerRepository ownerRepository;

	private final PetTypeRepository petTypeRepository;

	private final Random random = new Random();

	// First names for generating owner data
	private static final String[] FIRST_NAMES = { "John", "Mary", "James", "Patricia", "Michael", "Linda", "Robert",
			"Barbara", "William", "Susan", "David", "Jessica", "Richard", "Sarah", "Charles", "Karen", "Christopher",
			"Donna", "Daniel", "Nancy", "Matthew", "Lisa", "Mark", "Betty", "Donald", "Margaret", "Steven", "Sandra",
			"Paul", "Ashley", "Andrew", "Kimberly", "Joshua", "Emily", "Kenneth", "Deborah", "Kevin", "Stephanie",
			"Brian", "Catherine", "Edward", "Cynthia", "Ronald", "Kathleen", "Timothy", "Shirley", "Jason", "Angela",
			"Jeffrey", "Brenda", "Ryan", "Pamela", "Jacob", "Nicole", "Gary", "Emma", "Nicholas", "Sophia" };

	// Last names for generating owner data
	private static final String[] LAST_NAMES = { "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller",
			"Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas",
			"Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark",
			"Ramirez", "Lewis", "Robinson", "Walker", "Young", "Allen", "King", "Wright", "Scott", "Torres", "Peterson",
			"Phillips", "Campbell", "Parker", "Evans", "Edwards", "Collins", "Reyes", "Stewart", "Morris", "Morales",
			"Murphy", "Cook", "Rogers", "Gutierrez", "Ortiz", "Morgan", "Peterson", "Cooper", "Peterson", "Brady" };

	// Cities for generating owner data
	private static final String[] CITIES = { "Madison", "Sun Prairie", "McFarland", "Windsor", "Monona", "Waunakee",
			"Verona", "Middleton", "Dane", "De Forest", "Oregon", "Fitchburg", "Stoughton", "Mount Horeb",
			"Mazomanie" };

	// Street names for generating owner data
	private static final String[] STREETS = { "Main St", "Oak Ave", "Elm St", "Maple Dr", "Cedar Ln", "Pine St",
			"Birch Rd", "Ash Ave", "Spruce Ln", "Walnut Dr", "Chestnut St", "Willow Ave", "Hickory Ln", "Poplar Dr",
			"Sycamore St", "Hazel Ave", "Magnolia Ln", "Laurel Dr", "Beech St", "Alder Ave" };

	// Pet names for generating test data
	private static final String[] PET_NAMES = { "Max", "Bella", "Charlie", "Luna", "Bailey", "Cooper", "Daisy", "Lucy",
			"Rocky", "Molly", "Buddy", "Sophie", "Duke", "Lola", "Hunter", "Sadie", "Oscar", "Maggie", "Tucker",
			"Penny" };

	public LoadTestDataGenerator(OwnerRepository ownerRepository, PetTypeRepository petTypeRepository) {
		this.ownerRepository = ownerRepository;
		this.petTypeRepository = petTypeRepository;
	}

	/**
	 * Generates additional test data for load testing.
	 * Creates owners and their associated pets.
	 * @param ownerCount target number of owners to generate
	 * @param petsPerOwner average number of pets per owner
	 */
	public void generateTestData(int ownerCount, int petsPerOwner) {
		List<PetType> petTypes = new ArrayList<>(petTypeRepository.findAll());
		if (petTypes.isEmpty()) {
			throw new IllegalStateException("No pet types found in database. Initialize pet types first.");
		}

		long existingOwners = ownerRepository.count();
		int ownersToCreate = Math.max(0, ownerCount - (int) existingOwners);

		for (int i = 0; i < ownersToCreate; i++) {
			Owner owner = generateOwner();
			ownerRepository.save(owner);

			// Add pets to owner
			int petCount = 1 + random.nextInt(Math.max(1, petsPerOwner));
			for (int j = 0; j < petCount; j++) {
				Pet pet = generatePet(petTypes);
				owner.addPet(pet);
			}
			ownerRepository.save(owner);
		}
	}

	/**
	 * Generates a random owner with valid data.
	 */
	private Owner generateOwner() {
		Owner owner = new Owner();
		owner.setFirstName(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)]);
		owner.setLastName(LAST_NAMES[random.nextInt(LAST_NAMES.length)]);
		owner.setAddress(generateAddress());
		owner.setCity(CITIES[random.nextInt(CITIES.length)]);
		owner.setTelephone(generatePhoneNumber());
		return owner;
	}

	/**
	 * Generates a random address.
	 */
	private String generateAddress() {
		int streetNumber = 100 + random.nextInt(9900);
		String streetName = STREETS[random.nextInt(STREETS.length)];
		return streetNumber + " " + streetName;
	}

	/**
	 * Generates a valid 10-digit phone number.
	 */
	private String generatePhoneNumber() {
		return String.format("%d%d%d%d%d%d%d%d%d%d", random.nextInt(10), random.nextInt(10), random.nextInt(10),
				random.nextInt(10), random.nextInt(10), random.nextInt(10), random.nextInt(10), random.nextInt(10),
				random.nextInt(10), random.nextInt(10));
	}

	/**
	 * Generates a random pet with valid data.
	 */
	private Pet generatePet(List<PetType> petTypes) {
		Pet pet = new Pet();
		pet.setName(PET_NAMES[random.nextInt(PET_NAMES.length)]);
		pet.setBirthDate(generateBirthDate());
		pet.setType(petTypes.get(random.nextInt(petTypes.size())));
		return pet;
	}

	/**
	 * Generates a random pet birth date (within last 15 years).
	 */
	private LocalDate generateBirthDate() {
		LocalDate now = LocalDate.now();
		int daysInPast = random.nextInt(15 * 365); // 0-15 years
		return now.minusDays(daysInPast);
	}

	/**
	 * Validates that test data was properly seeded in database.
	 * @return TestDataValidation result with owner and pet counts
	 */
	public TestDataValidation validateTestData() {
		long ownerCount = ownerRepository.count();
		long petCount = 0;

		// Count total pets across all owners
		Iterable<Owner> allOwners = ownerRepository.findAll();
		for (Owner owner : allOwners) {
			petCount += owner.getPets().size();
		}

		TestDataValidation validation = new TestDataValidation();
		validation.setOwnerCount(ownerCount);
		validation.setPetCount(petCount);
		validation.setValid(ownerCount >= 100 && petCount >= 200);
		return validation;
	}

	/**
	 * Result class for test data validation.
	 */
	public static class TestDataValidation {

		private long ownerCount;

		private long petCount;

		private boolean valid;

		public long getOwnerCount() {
			return ownerCount;
		}

		public void setOwnerCount(long ownerCount) {
			this.ownerCount = ownerCount;
		}

		public long getPetCount() {
			return petCount;
		}

		public void setPetCount(long petCount) {
			this.petCount = petCount;
		}

		public boolean isValid() {
			return valid;
		}

		public void setValid(boolean valid) {
			this.valid = valid;
		}

		@Override
		public String toString() {
			return "TestDataValidation{" + "ownerCount=" + ownerCount + ", petCount=" + petCount + ", valid=" + valid
					+ '}';
		}

	}

}
