# Spring PetClinic Build Variants

This document describes the available build variants for Spring PetClinic, allowing you to test the application across different Java versions and threading models.

## Quick Start

### Maven: Java 21 Traditional (Default)

```bash
# Build with Java 21 traditional platform threads
./mvnw clean package -Pjava21-traditional

# Run with traditional profile
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-traditional
```

### Maven: Java 17 Baseline

```bash
# Build with Java 17
./mvnw clean package -Pjava17-baseline

# Run with default profile
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

### Gradle: Java 21

```bash
# Build with Java 21
JAVA_VERSION=21 ./gradlew clean build

# Run with traditional profile
java -jar build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-traditional
```

---

## Available Variants

### 1. Java 17 Baseline (Default)

**Profile ID:** `java17-baseline` (Maven only)

**Java Version:** 17

**Threading:** Platform threads

**Features:**
- Traditional POJO domain models (no records)
- Standard Spring Boot configuration
- Baseline for performance comparisons

**Build:**
```bash
./mvnw clean package -Pjava17-baseline
```

**Run:**
```bash
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

---

### 2. Java 21 Traditional Variant (Recommended for Language Feature Testing)

**Profile ID:** `java21-traditional` (Maven)

**Java Version:** 21

**Threading:** Platform threads (NO virtual threads)

**Features:**
- ✅ Records (6 domain models)
- ✅ Pattern matching in controllers
- ✅ Switch expressions for clean conditionals
- ❌ NO virtual threads (explicitly disabled)

**Purpose:** Control variant to measure language feature improvements independently from threading model changes

**Build:**
```bash
./mvnw clean package -Pjava21-traditional
```

**Run:**
```bash
# Option 1: Command-line flag
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-traditional

# Option 2: Environment variable
export SPRING_PROFILES_ACTIVE=java21-traditional
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# Option 3: During Maven build
./mvnw clean package -Pjava21-traditional spring-boot:run
```

**Configuration File:** `src/main/resources/application-java21-traditional.properties`

**Expected Metrics:**
- **Throughput:** Slight improvement over Java 17 (2-5%) from records and pattern matching optimization
- **Latency:** Similar to Java 17 (same threading model)
- **Memory:** Reduced from smaller record objects
- **Code:** 25% less LOC in domain models

**Documentation:** See [JAVA21-TRADITIONAL-VARIANT.md](JAVA21-TRADITIONAL-VARIANT.md) for detailed information

---

### 3. Java 21 Virtual Threads Variant (For I/O Performance Testing)

**Profile ID:** `java21-virtual` (Maven, activation based on Spring profile)

**Java Version:** 21

**Threading:** Virtual threads (Project Loom) for I/O-bound operations

**Features:**
- ✅ Records (same as Traditional)
- ✅ Pattern matching (same as Traditional)
- ✅ Switch expressions (same as Traditional)
- ✅ Virtual threads enabled in Tomcat
- ✅ Aggressive connection pooling (50 connections)
- ✅ High concurrency support (10,000+ virtual threads)

**Purpose:** Demonstrates how virtual threads handle I/O-bound workloads efficiently

**Build:**
```bash
./mvnw clean package -Pjava21-virtual
```

**Run:**
```bash
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=vthreads
```

**Configuration File:** `src/main/resources/application-vthreads.properties`

**Expected Metrics:**
- **Throughput:** Significant improvement (20-50%+) for concurrent I/O operations
- **Latency:** Reduced tail latency under high load
- **Memory:** Reduced thread memory overhead (1KB per virtual thread vs 1MB per platform thread)
- **Concurrency:** Can handle thousands of concurrent connections

**Note:** Virtual threads provide benefits primarily for I/O-bound operations. CPU-bound workloads show minimal improvement.

---

## Selecting a Variant

### For Development

```bash
# Default: uses Java 17 baseline
./mvnw spring-boot:run

# Or explicitly with Java 21
./mvnw clean package -Pjava21-traditional
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar --spring.profiles.active=java21-traditional
```

### For Testing

Run full test suite with all variants:

```bash
# Test with Java 17
./mvnw clean test -Pjava17-baseline

# Test with Java 21 traditional
./mvnw clean test -Pjava21-traditional

# Test with Java 21 virtual threads
./mvnw clean test -Pjava21-virtual
```

### For Benchmarking

```bash
# Build all variants
./mvnw clean package -Pjava17-baseline
./mvnw clean package -Pjava21-traditional
./mvnw clean package -Pjava21-virtual

# Run benchmark suite
java -jar spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-traditional

# Run JFR (Java Flight Recorder) monitoring
java -XX:StartFlightRecording=filename=recording.jfr \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-traditional
```

---

## Profile Activation Methods

### Maven Command-Line

```bash
# During build
./mvnw clean package -Pjava21-traditional

# Multiple profiles
./mvnw clean package -Pjava21-traditional,css
```

### Runtime Command-Line

```bash
java -jar app.jar --spring.profiles.active=java21-traditional
```

### Environment Variable

```bash
export SPRING_PROFILES_ACTIVE=java21-traditional
java -jar app.jar
```

### application.properties

```properties
# src/main/resources/application.properties
spring.profiles.active=java21-traditional
```

### IDE (IntelliJ IDEA)

1. Run → Edit Configurations
2. VM Options: `-Dspring.profiles.active=java21-traditional`
3. Or Program Arguments: `--spring.profiles.active=java21-traditional`

### IDE (Eclipse/STS)

1. Run Configurations → Arguments
2. Program Arguments: `--spring.profiles.active=java21-traditional`

---

## Maven Profile Properties

### java17-baseline

```xml
<profile>
  <id>java17-baseline</id>
  <properties>
    <java.version>17</java.version>
    <maven.compiler.release>17</maven.compiler.release>
  </properties>
</profile>
```

### java21-traditional

```xml
<profile>
  <id>java21-traditional</id>
  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
  </properties>
</profile>
```

### java21-virtual

```xml
<profile>
  <id>java21-virtual</id>
  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
  </properties>
  <activation>
    <property>
      <name>spring.profiles.active</name>
      <value>vthreads</value>
    </property>
  </activation>
</profile>
```

---

## Gradle Variant Selection

The Gradle build uses environment variables for variant selection:

```bash
# Default: Java 17
./gradlew clean build

# Java 21 Traditional
JAVA_VERSION=21 JAVA21_VARIANT=traditional ./gradlew clean build

# Java 21 Virtual Threads
JAVA_VERSION=21 JAVA21_VARIANT=virtual ./gradlew clean build
```

Or set in your shell:

```bash
export JAVA_VERSION=21
export JAVA21_VARIANT=traditional
./gradlew clean build
```

---

## Configuration File Mapping

| Variant | Spring Profile | Properties File |
|---------|---|---|
| Java 17 Baseline | (default) | `application.properties` |
| Java 21 Traditional | `java21-traditional` | `application-java21-traditional.properties` |
| Java 21 Virtual | `vthreads` | `application-vthreads.properties` |

### Spring Profile Loading Order

Spring loads configuration files in this order:

1. `application.properties` (default)
2. `application-{profile}.properties` (if profile is active)
3. Environment variables
4. System properties

**Example:** With `--spring.profiles.active=java21-traditional`:
1. Loads `application.properties` (base config)
2. Loads `application-java21-traditional.properties` (overrides for this variant)

---

## Verification Checklist

After selecting a variant, verify:

- [ ] Correct Java version is in use: `java --version`
- [ ] Application starts without errors
- [ ] Web interface is accessible at `http://localhost:8080`
- [ ] Database is initialized correctly
- [ ] No exceptions in logs related to threading or configuration

```bash
# Quick check
curl http://localhost:8080/
# Expected: HTML response with PetClinic application
```

---

## Performance Comparison Methodology

To fairly compare variants:

1. **Warm up:** Run 5 minutes of requests before measuring
2. **Load:** Use consistent load across all variants
3. **Metrics:** Collect throughput, latency, memory, CPU, thread count
4. **Tools:** Use JMH for microbenchmarks, JFR for application profiling
5. **Repeat:** Run 3+ times per variant to ensure consistency

**Expected Results:**

| Metric | Java 17 | Java 21 Traditional | Java 21 Virtual |
|--------|---------|-------------------|-----------------|
| Baseline | 100% | 102-105% | 120-150%+ |
| Tail Latency (p95) | 100% | 98-102% | 80-95% |
| Memory | 100% | 98-99% | 95-98% |
| GC Pauses | 100% | 98-102% | 98-102% |

*Percentages shown are relative to Java 17 baseline. Virtual threads show benefit under high concurrent I/O load.*

---

## Troubleshooting

### Issue: "Invalid compiler release version 21"

**Solution:** Install Java 21 JDK and set it as default:
```bash
java --version  # Should show 21.x.x
# On macOS: export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Issue: Profile not recognized

**Solution:** Check Maven profiles:
```bash
./mvnw help:active-profiles
```

### Issue: Virtual threads appear in traditional variant

**Solution:** Ensure you're not activating both profiles:
```bash
# WRONG: combines configurations
--spring.profiles.active=java21-traditional,vthreads

# CORRECT: only traditional
--spring.profiles.active=java21-traditional
```

### Issue: Records not compiling

**Solution:** Verify Java 21 compiler is active:
```bash
./mvnw clean compile -Pjava21-traditional -e
```

---

## Related Documentation

- [JAVA21-TRADITIONAL-VARIANT.md](JAVA21-TRADITIONAL-VARIANT.md) — Detailed variant guide
- [MODERNIZATION-REPORT.md](MODERNIZATION-REPORT.md) — Records conversion details
- [README.md](README.md) — General setup and running instructions

---

**Last Updated:** 2025-01-24
**Variants Supported:** Java 17, Java 21 Traditional, Java 21 Virtual
**Build Tools:** Maven 4.0+, Gradle 8.0+
