# Java 21 Documentation Index

Complete guide to all Java 21 variant documentation for Spring PetClinic.

---

## Quick Start (Read First)

### 1-Minute Quick Reference
**File:** [QUICK-REFERENCE-JAVA21.md](QUICK-REFERENCE-JAVA21.md)
- **Time:** 5 minutes
- **Content:** 
  - One-minute build and run commands
  - Code examples (records, pattern matching, switch expressions)
  - Quick verification checklist
  - Troubleshooting quick tips
- **Best For:** Getting started immediately

### 5-Minute Variant Overview
**File:** [VARIANTS.md](VARIANTS.md)
- **Time:** 10 minutes
- **Content:**
  - All three variants explained (Java 17, J21 Traditional, J21 Virtual)
  - When to use each variant
  - Profile activation methods (5 ways)
  - Configuration comparison
- **Best For:** Choosing between variants

### 10-Minute Setup Guide
**File:** [JAVA21-TRADITIONAL-SETUP-SUMMARY.md](JAVA21-TRADITIONAL-SETUP-SUMMARY.md)
- **Time:** 15 minutes
- **Content:**
  - Deliverables checklist
  - Build commands (Maven, Gradle, IDE)
  - Testing instructions
  - Verification procedures
- **Best For:** Complete setup from scratch

---

## Comprehensive Guides (Reference)

### Full Technical Guide
**File:** [JAVA21-TRADITIONAL-VARIANT.md](JAVA21-TRADITIONAL-VARIANT.md)
- **Time:** 20 minutes
- **Content:**
  - Complete overview and purpose
  - What changed from Java 17 (side-by-side comparisons)
  - Traditional threading explanation
  - Building and running (multiple methods)
  - Profile activation (5 different approaches)
  - Testing and verification
  - Performance expectations
  - Java 21 feature reference
  - Comparison matrix
  - Troubleshooting guide
  - Git branch configuration
- **Best For:** Complete understanding of the variant

### Task Completion Summary
**File:** [TASK-14-COMPLETION-SUMMARY.md](TASK-14-COMPLETION-SUMMARY.md)
- **Time:** 10 minutes
- **Content:**
  - Task completion checklist
  - Deliverables summary
  - Key features of variant
  - Build and run examples
  - Metrics and impact
  - Success criteria verification
- **Best For:** Project managers and reviewers

---

## Configuration Files

### Java 21 Traditional Configuration
**File:** `src/main/resources/application-java21-traditional.properties`

**Key Settings:**
```properties
# Platform threads (200 max)
server.tomcat.threads.max=200

# Conservative connection pool
spring.datasource.hikari.maximum-pool-size=20

# NO virtual threads
# (server.tomcat.virtual-threads.enabled is not set)
```

**Purpose:** Control variant configuration (platform threads only)

### Compare to Virtual Threads Configuration
**File:** `src/main/resources/application-vthreads.properties`

**Differences:**
```properties
# Virtual threads enabled
server.tomcat.virtual-threads.enabled=true

# High concurrency threads
server.tomcat.threads.max=10000

# Aggressive connection pool
spring.datasource.hikari.maximum-pool-size=50
```

---

## Source Code Examples

### Records (Domain Models)
**Location:** `src/main/java/org/springframework/samples/petclinic/owner/Pet.java`
**Type:** Java 21 Record

```java
@Entity
@Table(name = "pets")
public record Pet(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Integer id,
    @Column @NotBlank String name,
    @Column @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate birthDate,
    @ManyToOne @JoinColumn(name = "type_id") PetType type,
    @OneToMany(...) Set<Visit> visits
) {
    // Custom constructors
    public Pet(String name, LocalDate birthDate, PetType type) {
        this(null, name, birthDate, type, new LinkedHashSet<>());
    }
    
    // Helper methods
    public void addVisit(Visit visit) { ... }
    public boolean isNew() { return this.id == null; }
}
```

### Pattern Matching (Controllers)
**Location:** `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`

**Pattern Matching with Guards:**
```java
// Validate birth date using pattern matching
LocalDate currentDate = LocalDate.now();
boolean isValidBirthDate = switch (pet.birthDate()) {
    case null -> true;
    case LocalDate bd when bd.isAfter(currentDate) -> {
        result.rejectValue("birthDate", "typeMismatch.birthDate");
        yield false;
    }
    case LocalDate bd -> true;
};
```

### Switch Expressions (Controllers)
**Location:** `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`

**Multi-way Dispatching:**
```java
// Clean switch expression replacing if-else chains
return switch (ownersResults.getTotalElements()) {
    case 0 -> {
        result.rejectValue("lastName", "notFound", "not found");
        yield "owners/findOwners";
    }
    case 1 -> {
        Owner foundOwner = ownersResults.iterator().next();
        yield "redirect:/owners/" + foundOwner.id();
    }
    default -> addPaginationModel(page, model, ownersResults);
};
```

---

## Build System Configuration

### Maven Profile
**Location:** `pom.xml`

```xml
<profile>
  <id>java21-traditional</id>
  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
  </properties>
</profile>
```

**Usage:**
```bash
./mvnw clean package -Pjava21-traditional
```

### Gradle Support
**Location:** `build.gradle`

**Configuration:**
```gradle
def javaVersion = System.getenv('JAVA_VERSION') ?: '17'

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(javaVersion as Integer)
  }
}
```

**Usage:**
```bash
JAVA_VERSION=21 ./gradlew clean build
```

---

## README Integration

### Updated Main README
**Location:** `README.md` - Java 21 Variants section

**What's New:**
- Java 21 Traditional Variant subsection
- Java 21 Virtual Threads Variant subsection
- Links to detailed documentation

---

## Documentation Roadmap

### For Different Audiences

#### Developers (Getting Started)
1. **Start:** [QUICK-REFERENCE-JAVA21.md](QUICK-REFERENCE-JAVA21.md) (5 min)
2. **Next:** Build and run the application
3. **Reference:** [JAVA21-TRADITIONAL-VARIANT.md](JAVA21-TRADITIONAL-VARIANT.md) for detailed info

#### DevOps / Build Engineers
1. **Start:** [VARIANTS.md](VARIANTS.md) - Profile activation section
2. **Next:** [JAVA21-TRADITIONAL-SETUP-SUMMARY.md](JAVA21-TRADITIONAL-SETUP-SUMMARY.md)
3. **Reference:** Build commands and Maven/Gradle sections

#### Performance Engineers / QA
1. **Start:** [JAVA21-TRADITIONAL-SETUP-SUMMARY.md](JAVA21-TRADITIONAL-SETUP-SUMMARY.md) - Performance Baseline section
2. **Next:** [JAVA21-TRADITIONAL-VARIANT.md](JAVA21-TRADITIONAL-VARIANT.md) - Performance expectations
3. **Reference:** Benchmarking methodology section

#### Project Managers / Reviewers
1. **Start:** [TASK-14-COMPLETION-SUMMARY.md](TASK-14-COMPLETION-SUMMARY.md)
2. **Next:** Success criteria verification table
3. **Reference:** Deliverables summary

#### Academic / Research
1. **Start:** [JAVA21-TRADITIONAL-VARIANT.md](JAVA21-TRADITIONAL-VARIANT.md) - Java 21 Features Reference
2. **Next:** Comparison matrix and code examples
3. **Reference:** JEP links and resources section

---

## Key Statistics

### Code Reduction
- **Domain Models:** 465 → 244 LOC (-48%)
- **Controllers:** 539 → 512 LOC (-5%)
- **Total:** 1,004 → 756 LOC (-25%)

### Modern Constructs Applied
- **Records:** 6 domain models
- **Pattern Matching:** 5+ uses
- **Switch Expressions:** 4+ uses
- **Optional Patterns:** Multiple uses

### Threading Configuration
- **Traditional Variant:** 200 max threads, 20 DB connections
- **Virtual Variant:** 10,000+ max threads, 50 DB connections
- **Expected Improvement:** +2-5% (language) vs +20-50%+ (virtual threads)

---

## Cross-References

### Related Modernization Documents
- [MODERNIZATION-REPORT.md](MODERNIZATION-REPORT.md) — Records conversion details
- [MODERNIZATION-IMPLEMENTATION-SUMMARY.md](MODERNIZATION-IMPLEMENTATION-SUMMARY.md) — Implementation checklist

### Previous Completed Work
- Task 13: Domain model modernization (records, pattern matching, switch expressions)
- Referenced Java 17 baseline for comparison

### Upcoming Work
- Task 15: Java 21 Virtual Threads Variant
- Benchmarking and comparison studies

---

## Verification Checklist

### Before Starting
- [ ] Java 21 JDK installed: `java --version` shows 21.x
- [ ] Maven wrapper available: `./mvnw --version`
- [ ] Gradle wrapper available: `./gradlew --version`

### During Development
- [ ] Read QUICK-REFERENCE-JAVA21.md for quick start
- [ ] Review JAVA21-TRADITIONAL-VARIANT.md for detailed understanding
- [ ] Check VARIANTS.md for variant comparison

### After Building
- [ ] Application compiles: No errors, no preview feature warnings
- [ ] Application runs: `http://localhost:8080/` accessible
- [ ] Tests pass: 100% pass rate
- [ ] Profile active: Logs show `java21-traditional` profile active

### For Benchmarking
- [ ] Refer to JAVA21-TRADITIONAL-SETUP-SUMMARY.md - Benchmarking Readiness section
- [ ] Follow methodology in JAVA21-TRADITIONAL-VARIANT.md - Benchmarking Strategy

---

## File Organization

```
PetClinic Root/
├── Documentation Files (Java 21 Variant)
│   ├── QUICK-REFERENCE-JAVA21.md ........................ [Start here: 5 min]
│   ├── VARIANTS.md ..................................... [Overview: 10 min]
│   ├── JAVA21-TRADITIONAL-VARIANT.md ................... [Detailed: 20 min]
│   ├── JAVA21-TRADITIONAL-SETUP-SUMMARY.md ............ [Setup: 15 min]
│   └── JAVA21-DOCUMENTATION-INDEX.md .................. [This file]
│
├── Configuration Files
│   ├── src/main/resources/application-java21-traditional.properties
│   ├── pom.xml (profile: java21-traditional)
│   └── build.gradle (Java 21 support)
│
├── Source Code (Java 21 Modern)
│   └── src/main/java/org/springframework/samples/petclinic/
│       ├── owner/ (Pet, Owner records, pattern matching)
│       └── vet/ (Vet, Specialty records)
│
└── Previous Work (Task 13 - Modernization)
    ├── MODERNIZATION-REPORT.md
    └── modernization-metrics.json
```

---

## Quick Links

### Build Variants
- **Java 17 Baseline:** `./mvnw clean package` (default)
- **Java 21 Traditional:** `./mvnw clean package -Pjava21-traditional`
- **Java 21 Virtual:** `./mvnw clean package -Pjava21-virtual`

### Run Commands
- **Traditional:** `java -jar app.jar --spring.profiles.active=java21-traditional`
- **Virtual:** `java -jar app.jar --spring.profiles.active=vthreads`

### View Configuration
- **Traditional:** `cat src/main/resources/application-java21-traditional.properties`
- **Virtual:** `cat src/main/resources/application-vthreads.properties`

### Review Code Examples
- **Records:** `src/main/java/org/springframework/samples/petclinic/owner/Pet.java`
- **Pattern Matching:** `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`
- **Switch Expressions:** `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`

---

## Questions & Answers

### Q: Should I read all documentation?
**A:** No. Start with [QUICK-REFERENCE-JAVA21.md](QUICK-REFERENCE-JAVA21.md) (5 min), then read detailed guides as needed.

### Q: What's the difference between Traditional and Virtual variants?
**A:** Traditional uses platform threads (for language feature comparison), Virtual uses Project Loom virtual threads (for high concurrency). See [VARIANTS.md](VARIANTS.md) comparison matrix.

### Q: How do I enable Java 21 for my build?
**A:** 
- **Maven:** `./mvnw -Pjava21-traditional clean package`
- **Gradle:** `JAVA_VERSION=21 ./gradlew clean build`

### Q: What if my IDE doesn't recognize Java 21?
**A:** Install Java 21 JDK, then configure IDE to use it. See JAVA21-TRADITIONAL-VARIANT.md - Troubleshooting section.

### Q: Can I use both variants in the same build?
**A:** No, they are independent variants. Build each separately and compare results.

### Q: Where are the benchmarking instructions?
**A:** See JAVA21-TRADITIONAL-SETUP-SUMMARY.md - Benchmarking Readiness section and JAVA21-TRADITIONAL-VARIANT.md - Benchmarking Strategy section.

---

## Document Versions

| File | Version | Date | Status |
|------|---------|------|--------|
| QUICK-REFERENCE-JAVA21.md | 1.0 | 2025-01-24 | Final |
| VARIANTS.md | 1.0 | 2025-01-24 | Final |
| JAVA21-TRADITIONAL-VARIANT.md | 1.0 | 2025-01-24 | Final |
| JAVA21-TRADITIONAL-SETUP-SUMMARY.md | 1.0 | 2025-01-24 | Final |
| JAVA21-DOCUMENTATION-INDEX.md | 1.0 | 2025-01-24 | Final |

---

## Support and Resources

### Official Java 21 References
- [JEP 395: Records](https://openjdk.org/jeps/395)
- [JEP 405: Pattern Matching for instanceof](https://openjdk.org/jeps/405)
- [JEP 441: Switch Expressions](https://openjdk.org/jeps/441)
- [JEP 443: Unnamed Patterns and Variables](https://openjdk.org/jeps/443)

### Spring Framework References
- [Spring Boot 4.0+ Java 21 Support](https://spring.io/blog/2025/01/23/spring-boot-4-0-has-landed)
- [Spring Data JPA with Records](https://spring.io/blog/2023/08/11/spring-data-jpa-3-2-0-rc1-released)
- [Hibernate 6.2+ Records](https://hibernate.org/orm/releases/#6-2-0)

### Project Links
- [Spring PetClinic GitHub](https://github.com/spring-projects/spring-petclinic)
- [Project Loom: Virtual Threads](https://openjdk.org/projects/loom/)

---

**Last Updated:** 2025-01-24  
**Status:** ✅ Complete and ready for use  
**Maintenance:** Will be updated as new Java 21 variants are added
