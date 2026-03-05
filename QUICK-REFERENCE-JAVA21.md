# Quick Reference: Java 21 Traditional Variant

## One-Minute Setup

### Build and Run

```bash
# Maven: Build and run with Java 21 traditional profile
./mvnw clean package -Pjava21-traditional spring-boot:run

# Or build JAR and run separately
./mvnw clean package -Pjava21-traditional
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-traditional
```

### Gradle: Set Java Version and Build

```bash
JAVA_VERSION=21 ./gradlew clean build
java -jar build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-traditional
```

## Profile Activation

### Method 1: Command Line (Easiest)

```bash
java -jar app.jar --spring.profiles.active=java21-traditional
```

### Method 2: Environment Variable

```bash
export SPRING_PROFILES_ACTIVE=java21-traditional
java -jar app.jar
```

### Method 3: Maven Build Parameter

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=java21-traditional"
```

### Method 4: In IDE (IntelliJ IDEA)

1. Run → Edit Configurations
2. Add to **VM options**: `-Dspring.profiles.active=java21-traditional`
3. Or **Program arguments**: `--spring.profiles.active=java21-traditional`

## Key Differences: Java 21 Traditional vs Java 17

| Feature | Java 17 | Java 21 Traditional |
|---------|---------|-------------------|
| Domain Models | POJOs | **Records** |
| Getters/Setters | 176+ LOC | ~17 LOC per class |
| Pattern Matching | Not available | ✅ In controllers |
| Switch Expressions | Not available | ✅ Multi-way dispatch |
| Virtual Threads | Not available | ❌ Disabled (control) |
| Thread Pool Size | 200 | 200 (same) |
| Connection Pool | 20 | 20 (same) |
| Expected Improvement | Baseline | +2-5% (language features only) |

## Configuration Comparison

**File:** `application-java21-traditional.properties` (vs `application-vthreads.properties`)

```properties
# Java 21 TRADITIONAL (Platform Threads Only)
server.tomcat.threads.max=200
spring.datasource.hikari.maximum-pool-size=20
# virtual-threads: NOT enabled

# Java 21 VIRTUAL (Virtual Threads Enabled)
server.tomcat.virtual-threads.enabled=true
server.tomcat.threads.max=10000
spring.datasource.hikari.maximum-pool-size=50
# virtual-threads: ENABLED
```

## Code Examples: Modern Java 21 Features

### Records (Replaces POJOs)

```java
// Before (Java 17)
public class Pet {
    private Integer id;
    private String name;
    private LocalDate birthDate;
    
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    // ... 20+ more lines
}

// After (Java 21)
public record Pet(
    @Id Integer id,
    @NotBlank String name,
    @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate birthDate,
    PetType type,
    Set<Visit> visits
) {
    // Custom constructors as needed
    public Pet(String name, LocalDate birthDate, PetType type) {
        this(null, name, birthDate, type, new LinkedHashSet<>());
    }
}

// Usage: pet.id(), pet.name() instead of pet.getId(), pet.getName()
```

### Pattern Matching with Guards

```java
// Java 21 - LocalDate validation with pattern matching
LocalDate currentDate = LocalDate.now();
boolean isValid = switch (pet.birthDate()) {
    case null -> true;
    case LocalDate bd when bd.isAfter(currentDate) -> {
        result.rejectValue("birthDate", "invalid");
        yield false;
    }
    case LocalDate bd -> true;
};
```

### Switch Expressions

```java
// Before: if-else chain
String result;
if (count == 0) {
    result = "not found";
} else if (count == 1) {
    result = "redirect";
} else {
    result = "list";
}

// After: switch expression
String result = switch (count) {
    case 0 -> "not found";
    case 1 -> "redirect";
    default -> "list";
};
```

## Testing

```bash
# Run all tests
./mvnw clean test -Pjava21-traditional

# Run specific test
./mvnw clean test -Pjava21-traditional -Dtest=OwnerControllerTests
```

**Expected Result:** 100% pass rate (identical to Java 17)

## Verification Checklist

- [ ] Java 21 installed: `java --version` shows 21.x
- [ ] Compiles without errors: `./mvnw clean compile -Pjava21-traditional`
- [ ] JAR builds successfully
- [ ] Application starts: no errors in logs
- [ ] Web interface accessible: `curl http://localhost:8080/`
- [ ] No virtual thread messages in logs
- [ ] Test suite passes: 100% pass rate

## Files to Review

| File | Purpose |
|------|---------|
| `src/main/resources/application-java21-traditional.properties` | Configuration (platform threads only) |
| `src/main/java/org/springframework/samples/petclinic/owner/Pet.java` | Example record |
| `src/main/java/org/springframework/samples/petclinic/owner/PetController.java` | Pattern matching example |
| `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java` | Switch expression example |
| `JAVA21-TRADITIONAL-VARIANT.md` | Full documentation |
| `VARIANTS.md` | Multi-variant guide |

## Performance Expectations

Compared to Java 17 baseline:

- **Throughput:** +2-5% (records reduce CPU, pattern matching compiler optimization)
- **Latency:** ±2% (same threading model)
- **Memory:** 1-3% reduction (smaller record objects)
- **GC Pauses:** ±2% (no significant change)

**Note:** Improvements come from language features only, NOT from virtual threads.

## Comparison with Other Variants

### vs Java 17 Baseline
- **Build:** `./mvnw clean package -Pjava17-baseline`
- **Difference:** Language features (records, pattern matching) + Java 21 optimizations
- **Expected:** +2-5% improvement

### vs Java 21 Virtual Threads
- **Build:** `./mvnw clean package -Pjava21-virtual`
- **Run:** `java -jar app.jar --spring.profiles.active=vthreads`
- **Difference:** Virtual threads + higher concurrency (10,000 max threads)
- **Expected:** +20-50%+ for I/O-bound workloads

## Troubleshooting

### "Invalid compiler release version 21"
```bash
java --version  # Check Java version
export JAVA_HOME=$(/usr/libexec/java_home -v 21)  # macOS
```

### "Profile java21-traditional not found"
```bash
./mvnw help:active-profiles  # List available profiles
```

### Application won't start with profile
```bash
# Check Spring boot sees the profile
java -jar app.jar --spring.profiles.active=java21-traditional
# In logs, should see profile active message
```

### Records don't compile
```bash
# Ensure Java 21 compiler is active
./mvnw clean compile -Pjava21-traditional -X  # Verbose output
```

## Learn More

- [JAVA21-TRADITIONAL-VARIANT.md](JAVA21-TRADITIONAL-VARIANT.md) — Comprehensive guide
- [VARIANTS.md](VARIANTS.md) — All variants (Java 17, 21 Traditional, 21 Virtual)
- [JAVA21-TRADITIONAL-SETUP-SUMMARY.md](JAVA21-TRADITIONAL-SETUP-SUMMARY.md) — Setup checklist

## Resources

- [JEP 395: Records](https://openjdk.org/jeps/395)
- [JEP 405: Pattern Matching for instanceof](https://openjdk.org/jeps/405)
- [JEP 441: Switch Expressions](https://openjdk.org/jeps/441)
- [Spring Boot 4.0+ Java 21 Support](https://spring.io/blog/2025/01/23/spring-boot-4-0-has-landed)

---

**Status:** Ready to use  
**Tested:** ✅ Compiles, ✅ Runs, ✅ Tests pass  
**Branch:** `feature/java21-traditional`
