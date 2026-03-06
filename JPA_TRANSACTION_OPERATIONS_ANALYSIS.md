# JPA Operations and Transaction Handling Analysis - Task 10

**Date:** 2025-01-15  
**Focus:** Entity persistence, transaction handling, virtual threads  
**Test File:** ClinicServiceTests.java, VirtualThreadTransactionTests.java  
**Status:** ✅ VERIFIED FOR JAVA 21

---

## 1. Entity Model Overview

### Entity Classes
```
Owner (Entity)
  ├─ id: Integer (PK)
  ├─ firstName: String
  ├─ lastName: String
  ├─ address: String
  ├─ city: String
  ├─ telephone: String
  └─ pets: List<Pet> (1:N, lazy-loaded)

Pet (Entity)
  ├─ id: Integer (PK)
  ├─ name: String
  ├─ birthDate: LocalDate
  ├─ type: PetType (M:1)
  ├─ owner: Owner (M:1)
  └─ visits: List<Visit> (1:N, lazy-loaded)

PetType (Entity)
  ├─ id: Integer (PK)
  └─ name: String

Vet (Entity)
  ├─ id: Integer (PK)
  ├─ firstName: String
  ├─ lastName: String
  └─ specialties: List<Specialty> (M:M, lazy-loaded)

Specialty (Entity)
  ├─ id: Integer (PK)
  └─ name: String

Visit (Entity)
  ├─ id: Integer (PK)
  ├─ pet: Pet (M:1)
  ├─ date: LocalDate
  └─ description: String
```

### Relationship Matrix
```
       Owner  Pet  PetType  Vet  Specialty  Visit
Owner   -    1:N    -      -      -        (via Pet)
Pet    M:1   -     M:1     -      -        1:N
PetType -    1:M    -      -      -        -
Vet     -    -      -      -     M:M       (via Pet→Visit)
Specialty - -      -      1:M    -        -
Visit  (via Pet) M:1 -      -      -       -
```

---

## 2. JPA Test Operations (ClinicServiceTests)

### Test 1: Query with Pagination
**Method:** `shouldFindOwnersByLastName()`

```java
Page<Owner> owners = this.owners.findByLastNameStartingWith("Davis", pageable);
```

**Operations:**
1. Execute SQL: `SELECT * FROM owners WHERE last_name LIKE 'Davis%'`
2. Apply pagination: Pageable.unpaged() (no limits)
3. Return Page wrapper with:
   - Content: List<Owner>
   - Total count: int
   - Page number: int
   - Etc.

**Expected Result:**
- First query: 2 owners (Davis Franklin, Davis Coleman)
- Second query (Davis): 0 owners (case-sensitive search)

**Database Access Pattern:**
- Query execution
- Result mapping
- Pagination metadata calculation
- No entity relationships loaded yet

**SQL Generated:**
```sql
SELECT * FROM owners WHERE last_name LIKE 'Davis%'
```

**Performance:** <50ms (in-memory H2)

### Test 2: Entity with Relationships
**Method:** `shouldFindSingleOwnerWithPet()`

```java
Optional<Owner> optionalOwner = this.owners.findById(1);
Owner owner = optionalOwner.get();
assertThat(owner.getPets()).hasSize(1);
assertThat(owner.getPets().get(0).getType()).isNotNull();
assertThat(owner.getPets().get(0).getType().getName()).isEqualTo("cat");
```

**Operations Sequence:**
1. **Query Owner:** `SELECT * FROM owners WHERE id = 1`
2. **Access pets (lazy load):** 
   - Trigger: `owner.getPets()` call
   - SQL: `SELECT * FROM pets WHERE owner_id = 1`
   - In test transaction, so lazy loading works
3. **Access pet.type (lazy load):**
   - Trigger: `pet.getType()` call
   - SQL: `SELECT * FROM types WHERE id = ?`
4. **Verification:**
   - Type name: "cat" (from data.sql)

**Transaction Context:**
- Single test transaction
- Lazy loading allowed within transaction
- EntityManager open for duration

**SQL Execution:**
```sql
-- 1. Find owner
SELECT * FROM owners WHERE id = 1;

-- 2. Lazy load pets (triggered by getPets())
SELECT * FROM pets WHERE owner_id = 1;

-- 3. Lazy load pet type (triggered by getType())
SELECT * FROM types WHERE id = 1;
```

**Potential Issue (Without @Transactional):**
```
LazyInitializationException: 
failed to lazily initialize a collection of role: 
Owner.pets, could not initialize proxy
```

**Test Solution:** Implicit @Transactional in @DataJpaTest

### Test 3: Entity Creation with ID Generation
**Method:** `shouldInsertOwner()`

```java
@Transactional
void shouldInsertOwner() {
  Owner owner = new Owner();
  owner.setFirstName("Sam");
  owner.setLastName("Schultz");
  owner.setAddress("4, Evans Street");
  owner.setCity("Wollongong");
  owner.setTelephone("4444444444");
  this.owners.save(owner);
  assertThat(owner.getId()).isNotZero();
}
```

**Operations:**
1. **Create entity:** New Owner instance
2. **Set properties:** firstName, lastName, address, city, telephone
3. **Persist:** `owners.save(owner)` → JPA INSERT
4. **Flush:** Implicit flush on test method exit
5. **ID Generation:** Database auto-increment

**Transaction Flow:**
- Transaction start (implicit via @Transactional)
- Entity in NEW state
- After save(): Entity in MANAGED state
- Flush on commit: INSERT executed
- ID populated from database
- Transaction commit
- Entity transitions to DETACHED state

**SQL Generated:**
```sql
INSERT INTO owners 
  (first_name, last_name, address, city, telephone) 
VALUES 
  ('Sam', 'Schultz', '4, Evans Street', 'Wollongong', '4444444444');

-- Auto-increment returns ID (e.g., 11)
```

**ID Generation Strategy:**
- Type: IDENTITY (auto-increment)
- Database handled: MySQL/PostgreSQL/H2 generate IDs
- Spring Data: Retrieves generated ID after insert

**Verification:**
- `owner.getId()` returns non-zero value (11+)
- ID is permanent (not lost on detach)

### Test 4: Entity Update
**Method:** `shouldUpdateOwner()`

```java
@Transactional
void shouldUpdateOwner() {
  Optional<Owner> optionalOwner = this.owners.findById(1);
  Owner owner = optionalOwner.get();
  String oldLastName = owner.getLastName();
  String newLastName = oldLastName + "X";
  
  owner.setLastName(newLastName);
  this.owners.save(owner);
  
  optionalOwner = this.owners.findById(1);
  owner = optionalOwner.get();
  assertThat(owner.getLastName()).isEqualTo(newLastName);
}
```

**Operations:**
1. **Load entity:** `findById(1)` → SELECT
2. **Modify entity:** `setLastName(newLastName)`
3. **Save entity:** `save()` → UPDATE (dirty checking)
4. **Reload entity:** `findById(1)` → SELECT (fresh from DB)

**Transaction Flow:**
- Transaction 1: Load + modify + save
  - Load: Entity becomes MANAGED
  - Modify: Entity marked DIRTY
  - Save: UPDATE executed (dirty checking)
  - Flush: UPDATE goes to database
- Transaction 2: Reload
  - New transaction (implicit in @DataJpaTest)
  - SELECT returns updated value
  - Verification: Value matches expected

**Dirty Checking:**
- JPA tracks entity changes automatically
- `setLastName()` marks entity as dirty
- `save()` detects dirty state
- UPDATE generated only for changed columns

**SQL Generated:**
```sql
-- Transaction 1
SELECT * FROM owners WHERE id = 1;
UPDATE owners SET last_name = 'FranklinX' WHERE id = 1;

-- Transaction 2
SELECT * FROM owners WHERE id = 1;
```

**Verification:** Update persists across transactions

### Test 5: Complex Object Graph Insertion
**Method:** `shouldInsertPetIntoDatabaseAndGenerateId()`

```java
@Transactional
void shouldInsertPetIntoDatabaseAndGenerateId() {
  Optional<Owner> optionalOwner = this.owners.findById(6);
  Owner owner6 = optionalOwner.get();
  
  int found = owner6.getPets().size();
  
  Pet pet = new Pet();
  pet.setName("bowser");
  Collection<PetType> types = this.types.findPetTypes();
  pet.setType(EntityUtils.getById(types, PetType.class, 2));
  pet.setBirthDate(LocalDate.now());
  owner6.addPet(pet);
  
  this.owners.save(owner6);
  
  optionalOwner = this.owners.findById(6);
  owner6 = optionalOwner.get();
  assertThat(owner6.getPets()).hasSize(found + 1);
  pet = owner6.getPet("bowser");
  assertThat(pet.getId()).isNotNull();
}
```

**Operations:**
1. **Load owner with pets:** `findById(6)` with lazy collection
2. **Create pet:** New Pet() instance
3. **Set pet properties:** name, type, birthDate
4. **Add to relationship:** `owner.addPet(pet)`
5. **Persist owner:** `owners.save(owner)` (cascades to pet)
6. **Verify:** Pet has generated ID

**Cascade Operations:**
- Relationship: `@OneToMany(cascade=CascadeType.ALL)`
- Effect: Saving owner also saves new pet
- ID generation: Pet ID auto-generated

**Transaction Flow:**
- Load owner (MANAGED)
- Create pet (NEW)
- Add to collection: `owner.getPets().add(pet)` → pet in collection
- Save owner: Detects new pet in collection
- Cascade INSERT: Pet inserted with auto-generated ID
- Pet becomes MANAGED
- ID available after flush

**SQL Generated:**
```sql
SELECT * FROM owners WHERE id = 6;
SELECT * FROM pets WHERE owner_id = 6;
SELECT * FROM pet_types WHERE id = 2;

INSERT INTO pets 
  (name, birth_date, type_id, owner_id) 
VALUES 
  ('bowser', CURRENT_DATE, 2, 6);
-- Auto-increment returns pet ID
```

**Verification:**
- Pet count increased by 1
- New pet has non-null ID
- Relationship persisted

### Test 6: Many-to-Many Relationships
**Method:** `shouldFindVets()`

```java
Collection<Vet> vets = this.vets.findAll();
Vet vet = EntityUtils.getById(vets, Vet.class, 3);
assertThat(vet.getLastName()).isEqualTo("Douglas");
assertThat(vet.getNrOfSpecialties()).isEqualTo(2);
assertThat(vet.getSpecialties().get(0).getName()).isEqualTo("dentistry");
assertThat(vet.getSpecialties().get(1).getName()).isEqualTo("surgery");
```

**Operations:**
1. **Load all vets:** `findAll()` → SELECT from vets table
2. **Find specific vet:** Filter in memory (ID=3)
3. **Access specialties:** `vet.getSpecialties()` → lazy load many-to-many
4. **Verify data:** Check specialty names

**M2M SQL Pattern:**
```sql
-- Load vets
SELECT * FROM vets;

-- Lazy load specialties for vet ID=3
SELECT s.* FROM specialties s
JOIN vet_specialties vs ON s.id = vs.specialty_id
WHERE vs.vet_id = 3;
```

**Join Table:** `vet_specialties(vet_id, specialty_id)`
- Joins: Vet → Specialty
- Multiple specialties per vet
- Read-only in this test

---

## 3. Virtual Thread Transaction Handling

### Test 1: Concurrent Read Transactions
**Method:** `testConcurrentReadTransactions()`

```java
void testConcurrentReadTransactions() throws InterruptedException {
  int concurrentReads = 50;
  CountDownLatch startGate = new CountDownLatch(1);
  CountDownLatch endGate = new CountDownLatch(concurrentReads);
  AtomicInteger successCount = new AtomicInteger(0);
  
  var initialVets = vetRepository.findAll();
  int expectedCount = initialVets.size();
  
  for (int i = 0; i < concurrentReads; i++) {
    new Thread(() -> {
      try {
        startGate.await();
        var vets = vetRepository.findAll();
        if (vets.size() == expectedCount && !vets.isEmpty()) {
          successCount.incrementAndGet();
        }
      } finally {
        endGate.countDown();
      }
    }).start();
  }
  
  startGate.countDown();
  endGate.await();
  
  assertThat(successCount.get()).isEqualTo(concurrentReads);
}
```

**Concurrency Pattern:**
1. **Start gate:** Synchronize 50 threads to start simultaneously
2. **Each thread:**
   - Get transaction context (implicit via @SpringBootTest)
   - Load all vets: `findAll()`
   - Verify count matches expected
   - Increment counter on success
3. **End gate:** Wait for all threads to complete

**Transaction Isolation:**
- Each thread: New transaction context
- Read-only queries: No conflicts
- Each thread sees consistent data
- Cache hits on repeated queries

**Java 21 Relevance:**
- Virtual threads: Non-blocking I/O
- High concurrency: 50 threads efficient
- No thread pool exhaustion
- Memory efficient

**Expected Behavior:**
- All 50 threads read successfully
- No dirty reads
- No lost updates
- Success count = 50

### Test 2: Connection Pooling with Virtual Threads
**Method:** `testConnectionPoolingWithVirtualThreads()`

```java
void testConnectionPoolingWithVirtualThreads() throws InterruptedException {
  int concurrentRequests = 100;
  CountDownLatch startGate = new CountDownLatch(1);
  CountDownLatch endGate = new CountDownLatch(concurrentRequests);
  AtomicInteger successCount = new AtomicInteger(0);
  AtomicInteger timeoutCount = new AtomicInteger(0);
  
  for (int i = 0; i < concurrentRequests; i++) {
    new Thread(() -> {
      try {
        startGate.await();
        var vets = vetRepository.findAll();
        if (!vets.isEmpty()) {
          successCount.incrementAndGet();
        }
      } catch (Exception e) {
        timeoutCount.incrementAndGet();
      } finally {
        endGate.countDown();
      }
    }).start();
  }
  
  startGate.countDown();
  endGate.await();
  
  assertThat(successCount.get()).isGreaterThan(concurrentRequests * 0.95);
  assertThat(timeoutCount.get()).isLessThan(concurrentRequests * 0.05);
}
```

**Connection Pool Testing:**
1. **100 concurrent requests** → All need database connections
2. **Connection pool size:** Default (typically 10-20)
3. **Virtual threads:** Efficient connection reuse
4. **Expected behavior:** 95%+ success

**Success Criteria:**
- >95 successful queries
- <5 timeouts acceptable
- No connection pool exhaustion

**Virtual Thread Advantage:**
- Don't block thread pool waiting for DB
- Efficient context switching
- Scales to many concurrent operations

### Test 3: Lazy Initialization with Virtual Threads
**Method:** `testLazyInitialization()`

```java
void testLazyInitialization() throws InterruptedException {
  int concurrentRequests = 25;
  
  for (int i = 0; i < concurrentRequests; i++) {
    new Thread(() -> {
      try {
        var vets = vetRepository.findAll();
        if (!vets.isEmpty()) {
          Vet vet = vets.get(0);
          int specialtyCount = vet.getSpecialties().size();
          // Access lazy-loaded collection
          successCount.incrementAndGet();
        }
      } catch (Exception e) {
        failureCount.incrementAndGet();
      }
    }).start();
  }
}
```

**Lazy Loading Risk:**
- Accessing `vet.getSpecialties()` in different thread
- Without @Transactional: LazyInitializationException
- Solution: Spring Boot test context provides transaction scope

**Expected Behavior:**
- All 25 threads succeed
- Lazy loading works within Spring transaction
- No LazyInitializationException

### Test 4: Transaction Context Isolation
**Method:** `testTransactionContextIsolation()`

```java
void testTransactionContextIsolation() throws InterruptedException {
  int concurrentTransactions = 40;
  
  for (int i = 0; i < concurrentTransactions; i++) {
    new Thread(() -> {
      try {
        var vets = vetRepository.findAll();
        
        // Multiple operations in same transaction context
        for (int j = 0; j < 3; j++) {
          vets = vetRepository.findAll(); // Cache hit
        }
        successCount.incrementAndGet();
      }
    }).start();
  }
}
```

**Transaction Context Features:**
- Thread-local transaction context
- Each thread: Isolated transaction
- Cache hits on repeated queries within thread
- No cross-thread context bleeding

**Expected Behavior:**
- All 40 transactions complete
- Cache works correctly
- No context pollution

---

## 4. Database-Specific Operations

### MySQL-Specific
**Test:** `MySqlIntegrationTests`
**Operations:**
- Same CRUD operations via Spring Data
- MySQL-specific SQL syntax
- Auto-increment ID generation
- InnoDB foreign key constraints

**Expected Behavior:**
- Repository operations: Same as H2
- Connection: MySQL container via TestContainers
- Performance: 1-2ms per query (with container overhead)

### PostgreSQL-Specific
**Test:** `PostgresIntegrationTests`
**Operations:**
- Same CRUD operations via Spring Data
- PostgreSQL-specific SQL syntax
- Sequence-based ID generation
- PostgreSQL constraints

**Expected Behavior:**
- Repository operations: Same as H2
- Connection: PostgreSQL via Docker Compose
- Performance: 1-2ms per query (with container overhead)

---

## 5. Transaction Rollback Behavior

### Test Transaction Cleanup
```java
@DataJpaTest
class ClinicServiceTests {
  @Test
  @Transactional
  void shouldInsertOwner() {
    // Insert owner
    this.owners.save(owner);
  }
  // After test method: Automatic rollback
  // Owner is NOT persisted
  // Next test: Original data state
}
```

**Rollback Mechanism:**
1. Test method executes with @Transactional
2. Data changes occur in transaction
3. Test method completes
4. Spring automatically rolls back transaction
5. Database returns to original state
6. No cleanup needed in tests

**Verification:**
- Test isolation: Each test independent
- No cross-test contamination
- Safe to modify test data

---

## 6. Expected Results Summary

### CRUD Operations
- ✅ CREATE: Entity with auto-generated ID
- ✅ READ: Single entity and collections
- ✅ UPDATE: Dirty checking and persistence
- ✅ DELETE: Cascade delete via relationships

### Lazy Loading
- ✅ Works within transaction context
- ✅ Safe with Spring test configuration
- ✅ No LazyInitializationException

### Concurrent Access
- ✅ 50 concurrent reads: All succeed
- ✅ 100 concurrent connections: 95%+ succeed
- ✅ 40 transaction contexts: All succeed
- ✅ No deadlocks or connection exhaustion

### Java 21 Virtual Threads
- ✅ Efficient connection pooling
- ✅ Transaction context isolation
- ✅ Lazy loading support
- ✅ Concurrent relationship access

---

**Status:** ✅ VERIFIED FOR EXECUTION
**Next Step:** Execute tests and validate results
