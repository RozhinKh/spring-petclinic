# Java 21 Virtual Threads Variant — Build & Activation Commands

**Quick Reference for Building and Running the Virtual Threads Variant**

---

## Maven Build Commands

### Clean Build (Compile + Package)

```bash
# Build Java 21 Virtual Threads variant
./mvnw clean package -Pjava21-virtual

# Expected output:
# [INFO] BUILD SUCCESS
# [INFO] Total time: ~30 seconds
```

### Build with Tests

```bash
# Build with full test suite
./mvnw clean package -Pjava21-virtual

# Run tests against compiled code
./mvnw test -Pjava21-virtual

# Expected: All tests pass (100% pass rate)
```

### Build JAR Only (Skip Tests)

```bash
./mvnw clean package -Pjava21-virtual -DskipTests
```

### Verify Build Without Creating JAR

```bash
./mvnw verify -Pjava21-virtual
```

---

## Gradle Build Commands

### Clean Build (Compile + Build)

```bash
# Using environment variables to select Java 21 and virtual threads variant
JAVA_VERSION=21 JAVA21_VARIANT=virtual ./gradlew clean build

# Expected output:
# BUILD SUCCESSFUL in ~30s
```

### Build with Tests

```bash
JAVA_VERSION=21 JAVA21_VARIANT=virtual ./gradlew test
```

### Build JAR Only (Skip Tests)

```bash
JAVA_VERSION=21 JAVA21_VARIANT=virtual ./gradlew build -x test
```

### Using Windows Command Prompt

```cmd
set JAVA_VERSION=21
set JAVA21_VARIANT=virtual
gradlew clean build
```

### Using PowerShell

```powershell
$env:JAVA_VERSION = '21'
$env:JAVA21_VARIANT = 'virtual'
./gradlew clean build
```

---

## Running the Application

### With Maven Build

```bash
# 1. Build the application
./mvnw clean package -Pjava21-virtual

# 2. Run the JAR with virtual thread profile
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-virtual

# Expected output:
# ...
# o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8080
# o.s.samples.petclinic.PetClinicApplication : Started PetClinicApplication in X.XXXs
```

### With Gradle Build

```bash
# 1. Build the application
JAVA_VERSION=21 JAVA21_VARIANT=virtual ./gradlew build

# 2. Run the JAR
java -jar build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-virtual
```

### Run in IDE (IntelliJ IDEA / Eclipse)

**Method 1: Using Run Configuration**

1. Right-click `PetClinicApplication.java` in IDE
2. Select `Run 'PetClinicApplication'`
3. Edit run configuration:
   - Set VM options: `-Dspring.profiles.active=java21-virtual`
   - Ensure JDK 21+ is selected as Project SDK

**Method 2: Using Boot Dashboard (Spring Tools Suite)**

1. Right-click project in Boot Dashboard
2. Select `(Re)start` → Application starts with default profile
3. To use virtual thread profile, set environment property

---

## Verification Commands

### Verify Application Started Successfully

```bash
# Check health endpoint
curl -s http://localhost:8080/actuator/health | jq .

# Expected response:
# {
#   "status": "UP",
#   ...
# }
```

### Verify Virtual Threads Are Enabled

```bash
# Check environment properties for virtual thread setting
curl -s http://localhost:8080/actuator/env | \
  jq '.propertySources[] | select(.name=="systemProperties") | .source' | \
  grep -i "virtual"

# Expected: spring.threads.virtual.enabled = true
```

### Verify Virtual Thread Configuration

```bash
# Check HikariCP pool size (should be 50 for virtual threads)
curl -s http://localhost:8080/actuator/health/db | jq .

# Expected: hikaripool shows appropriate pool size
```

### Test Database Operations (Virtual Thread Execution)

```bash
# Create a new owner (triggers database operation on virtual thread)
curl -X POST http://localhost:8080/owners/new \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "firstName=Test&lastName=User&city=Springfield&address=123%20Main&telephone=5551234567"

# Response indicates successful database save via virtual thread
```

### Monitor Virtual Threads During Load

```bash
# Get thread info (requires jcmd command)
jcmd <pid> Thread.print > threads.txt

# Count virtual threads
grep -c "virtual" threads.txt

# Expected: Many virtual thread entries (com.oracle.cds.virtual.VirtualThread)
```

---

## Comparison: All Three Variants

### Quick Build Comparison

| Variant | Maven Command | Gradle Command |
|---------|---------------|----------------|
| **Java 17 Baseline** | `./mvnw clean package` | `./gradlew clean build` |
| **Java 21 Traditional** | `./mvnw clean package -Pjava21-traditional` | `JAVA_VERSION=21 JAVA21_VARIANT=traditional ./gradlew clean build` |
| **Java 21 Virtual** | `./mvnw clean package -Pjava21-virtual` | `JAVA_VERSION=21 JAVA21_VARIANT=virtual ./gradlew clean build` |

### Run Command Comparison

| Variant | Run Command |
|---------|------------|
| **Java 17 Baseline** | `java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar` |
| **Java 21 Traditional** | `java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar --spring.profiles.active=java21-traditional` |
| **Java 21 Virtual** | `java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar --spring.profiles.active=java21-virtual` |

---

## Troubleshooting Build Issues

### Issue: Profile Not Found

```
ERROR] The active profile "java21-virtual" could not be found in the offline mode and the repositories are blocked, offline mode should be turned off.
```

**Solution:** Ensure Maven can access Maven Central:
```bash
./mvnw clean package -Pjava21-virtual -o  # Remove -o flag
```

### Issue: Java Version Mismatch (Maven)

```
ERROR Java 21 or later is required
```

**Solution:** Ensure Java 21+ is active:
```bash
java -version  # Should show Java 21+
./mvnw --version  # Should use Java 21+
```

### Issue: Java Version Mismatch (Gradle)

```
ERROR Unsupported class version
```

**Solution:** Ensure environment variable is set:
```bash
JAVA_VERSION=21 ./gradlew clean build
```

### Issue: Virtual Threads Not Enabled in Running App

```
spring.threads.virtual.enabled is not true
```

**Solution:** Ensure profile is set when running:
```bash
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-virtual
```

---

## Development Workflow

### Complete Workflow for Development

```bash
# 1. Clean any previous builds
./mvnw clean

# 2. Build with tests
./mvnw clean package -Pjava21-virtual

# 3. Start the application
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=java21-virtual

# 4. In another terminal, verify it's running
curl http://localhost:8080/owners

# 5. Run load test (optional)
ab -n 100 -c 10 http://localhost:8080/owners
```

### CI/CD Pipeline Setup

**GitHub Actions Example:**

```yaml
name: Build Java 21 Virtual Threads

on: [push]

jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java-version: [21]
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 21
        uses: actions/setup-java@v2
        with:
          java-version: '21'
      - name: Build with Maven
        run: ./mvnw clean package -Pjava21-virtual
      - name: Run tests
        run: ./mvnw test -Pjava21-virtual
```

---

## Build Profile Details

### Maven Profile: java21-virtual

**Location:** `pom.xml` lines 466-478

**Properties Set:**
- `java.version=21`
- `maven.compiler.release=21`

**Activation:**
- Manually with `-Pjava21-virtual` flag
- Automatically when `spring.profiles.active=java21-virtual` is set at runtime

### Gradle Configuration

**Location:** `build.gradle` lines 18-31

**Environment Variables:**
- `JAVA_VERSION=21` — Selects Java 21 toolchain
- `JAVA21_VARIANT=virtual` — Activates virtual thread variant

**Variant Detection:**
```gradle
def isVirtualThreadsEnabled = 
  javaVersion == '21' && isJava21Variant == 'virtual'
```

---

## Next Steps After Building

1. **Verify Application:** See "Verification Commands" section above
2. **Run Tests:** `./mvnw test -Pjava21-virtual`
3. **Load Test:** See [LOAD-TESTING-GUIDE.md](LOAD-TESTING-GUIDE.md)
4. **Benchmark:** See [BENCHMARK-QUICK-START.md](BENCHMARK-QUICK-START.md)
5. **Compare Variants:** See [VARIANTS.md](VARIANTS.md)

---

## Summary

### Key Commands

```bash
# Maven: Build and run
./mvnw clean package -Pjava21-virtual
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar --spring.profiles.active=java21-virtual

# Gradle: Build and run
JAVA_VERSION=21 JAVA21_VARIANT=virtual ./gradlew clean build
java -jar build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar --spring.profiles.active=java21-virtual

# Verify
curl http://localhost:8080/owners
```

### For Detailed Information

- **Architecture & Design:** See [JAVA21-VIRTUAL-VARIANT.md](JAVA21-VIRTUAL-VARIANT.md)
- **Virtualization Points:** See [VIRTUALIZATION-POINTS-REPORT.md](VIRTUALIZATION-POINTS-REPORT.md)
- **Performance:** See [LOAD-TEST-IMPLEMENTATION-SUMMARY.md](LOAD-TEST-IMPLEMENTATION-SUMMARY.md)
- **Variant Comparison:** See [VARIANTS.md](VARIANTS.md)

