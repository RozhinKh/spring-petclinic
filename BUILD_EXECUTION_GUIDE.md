# Build Execution Guide - Spring PetClinic with Java 21

**Last Updated:** 2024-11-28
**Java Version:** 21
**Maven:** 3.9.12
**Gradle:** 9.2.1

---

## Quick Start

### Prerequisites

- **Java 21** installed and available in PATH
- **Git** for version control
- **Docker** (optional, for integration tests)

### Verify Java Installation

```bash
java -version
# Expected output:
# openjdk version "21.x.x" ...
# OpenJDK Runtime Environment ...

javac -version
# Expected output:
# javac 21.x.x
```

---

## Maven Build Commands

### 1. Clean Compilation (No Tests)

```bash
./mvnw clean compile
```

**What it does:**
- Removes previous build artifacts
- Compiles all source code from `src/main/java`
- Validates Java 21 requirement
- Executes code formatting checks
- Executes checkstyle validation

**Expected output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: X.XXs
```

**Duration:** 10-15 seconds (first time), 5-8 seconds (subsequent)

---

### 2. Package Build (Compile + Unit Tests)

```bash
./mvnw clean package
```

**What it does:**
- Performs clean compilation
- Executes all unit tests from `src/test/java`
- Packages compiled code into JAR
- Generates code coverage report (JaCoCo)
- Creates Spring Boot executable JAR

**Expected output:**
```
[INFO] BUILD SUCCESS
[INFO] Artifact: target/spring-petclinic-4.0.0-SNAPSHOT.jar
[INFO] Total time: X.XXs
```

**Duration:** 25-35 seconds

**Generated artifacts:**
- `target/spring-petclinic-4.0.0-SNAPSHOT.jar` (executable JAR, ~50MB)
- `target/classes/` (compiled application classes)
- `target/test-classes/` (compiled test classes)
- `target/site/jacoco/index.html` (code coverage report)

---

### 3. Full Verification (Package + Integration Tests)

```bash
./mvnw clean verify
```

**What it does:**
- Performs package build
- Executes all integration tests
- Validates native image build (GraalVM)
- Generates SBOM (Software Bill of Materials)
- Performs final verification checks

**Expected output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: X.XXs
```

**Duration:** 45-60 seconds (includes TestContainers startup)

**Generated artifacts:**
- All package artifacts (see above)
- `target/native/` (native image binaries, if enabled)
- `target/cyclonedx/bom.xml` (SBOM file)
- Integration test reports

---

### 4. Install to Local Repository

```bash
./mvnw clean install
```

**What it does:**
- Performs verify build
- Installs JAR to local Maven repository (~/.m2/repository)
- Available for dependency by other local projects

**Expected output:**
```
[INFO] BUILD SUCCESS
[INFO] Installed to ~/.m2/repository/org/springframework/samples/spring-petclinic/4.0.0-SNAPSHOT/
```

**Duration:** 50-65 seconds

---

### 5. Skip Tests (Faster Build)

```bash
./mvnw clean package -DskipTests
```

**What it does:**
- Fast build without test execution
- Useful for development builds
- Still performs compilation and packaging

**Expected output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: X.XXs
```

**Duration:** 8-12 seconds

---

### 6. Parallel Test Execution

```bash
./mvnw clean verify -T 1C
```

**What it does:**
- Compile using one thread per core
- Execute tests in parallel
- Faster on multi-core systems

**Duration:** 30-40 seconds (faster than serial)

---

### 7. Build with Additional Logging

```bash
./mvnw clean package -X
```

**What it does:**
- Enables debug mode
- Shows detailed build information
- Useful for troubleshooting

**Note:** Produces very verbose output

---

### 8. Update Dependencies

```bash
./mvnw dependency:tree
./mvnw versions:display-dependency-updates
./mvnw versions:display-plugin-updates
```

**What it does:**
- Show dependency tree
- Display available updates
- Identify obsolete versions

---

## Gradle Build Commands

### 1. Clean Build (Compile + Tests)

```bash
./gradlew clean build
```

**What it does:**
- Removes previous build artifacts
- Compiles all source code
- Executes all unit and integration tests
- Runs code quality checks
- Packages into executable JAR

**Expected output:**
```
BUILD SUCCESSFUL in Xs
```

**Duration:** 25-40 seconds (first time), 15-20 seconds (with cache)

**Generated artifacts:**
- `build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar` (executable JAR, ~50MB)
- `build/classes/` (compiled classes)
- `build/test-results/` (test reports)
- Build cache in `build/.gradle/`

---

### 2. Assemble Only (No Tests)

```bash
./gradlew clean assemble
```

**What it does:**
- Compiles source code
- Packages into JAR
- Does NOT execute tests

**Expected output:**
```
BUILD SUCCESSFUL in Xs
```

**Duration:** 8-12 seconds

**Generated artifacts:**
- `build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar` (executable JAR)

---

### 3. Test Only

```bash
./gradlew test
```

**What it does:**
- Executes unit tests only
- Does not recompile if code unchanged
- Uses build cache for fast execution

**Expected output:**
```
BUILD SUCCESSFUL in Xs
```

**Duration:** 5-8 seconds (with cache)

---

### 4. Build with Parallel Execution

```bash
./gradlew clean build --parallel --max-workers=4
```

**What it does:**
- Executes tasks in parallel
- Uses up to 4 worker threads
- Faster on multi-core systems

**Duration:** 15-25 seconds

---

### 5. Incremental Build (Fastest)

```bash
./gradlew build
```

**What it does:**
- No clean, uses build cache
- Only rebuilds changed files
- Leverages Gradle's incremental build system

**Expected output:**
```
BUILD SUCCESSFUL in Xs (from cache)
```

**Duration:** 2-5 seconds (after first build)

---

### 6. Build with Additional Memory

```bash
./gradlew clean build -Dorg.gradle.jvmargs="-Xmx2g"
```

**What it does:**
- Allocates 2GB heap to Gradle process
- Useful for large projects
- Prevents out-of-memory errors

---

### 7. Check Dependencies

```bash
./gradlew dependencies
./gradlew dependencyUpdates
```

**What it does:**
- Show full dependency tree
- Display available updates
- Identify outdated versions

---

### 8. Build with Verbose Output

```bash
./gradlew build -i
```

**What it does:**
- Shows info-level logging
- Displays task execution details
- Useful for troubleshooting

---

## Running the Application

### From Maven Build

```bash
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

### From Gradle Build

```bash
java -jar build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar
```

### Using Spring Boot Maven Plugin

```bash
./mvnw spring-boot:run
```

### Using Gradle bootRun Task

```bash
./gradlew bootRun
```

### Expected Startup Output

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_|\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v4.0.1)

2024-11-28 14:37:52.XXX  INFO ... : Starting PetClinicApplication using Java 21.0.x on ...
2024-11-28 14:37:53.XXX  INFO ... : No active profile set, falling back to default profiles: [default]
2024-11-28 14:37:54.XXX  INFO ... : Tomcat initialized with port(s): 8080 (http)
2024-11-28 14:37:54.XXX  INFO ... : Tomcat started on port(s): 8080
2024-11-28 14:37:54.XXX  INFO ... : Started PetClinicApplication in X.XXXs using Java 21.0.x
```

**Access the application:**
- URL: http://localhost:8080
- Stop with: Ctrl+C

---

## Build Comparison

| Aspect | Maven | Gradle |
|--------|-------|--------|
| **Clean Compile** | `./mvnw clean compile` | `./gradlew clean build -x test` |
| **Package** | `./mvnw clean package` | `./gradlew clean assemble` |
| **Full Build** | `./mvnw clean verify` | `./gradlew clean build` |
| **Test Only** | `./mvnw test` | `./gradlew test` |
| **Skip Tests** | `-DskipTests` | `-x test` |
| **Parallel** | `-T 1C` | `--parallel` |
| **Incremental** | Not native | Default behavior |
| **Cache** | Plugin caches | Full build cache |

---

## Troubleshooting

### Build Fails: "Java version not compatible"

**Solution:**
```bash
# Verify Java 21 is installed
java -version

# If not, ensure JAVA_HOME points to Java 21
export JAVA_HOME=/path/to/java/21
./mvnw clean compile  # or ./gradlew build
```

### Build Fails: "Out of memory"

**For Maven:**
```bash
export MAVEN_OPTS="-Xmx2g"
./mvnw clean verify
```

**For Gradle:**
```bash
./gradlew build -Dorg.gradle.jvmargs="-Xmx2g"
```

### Build Fails: "Cannot connect to Docker"

**For integration tests that require Docker:**
```bash
# Ensure Docker daemon is running
docker ps

# Skip integration tests (Maven)
./mvnw clean package -DskipTests

# Skip integration tests (Gradle)
./gradlew clean assemble -x test
```

### Slow Build Performance

**Optimizations:**

Maven:
```bash
# Use daemon for faster builds
mvnd clean verify

# Or increase parallel threads
./mvnw clean verify -T 1C
```

Gradle:
```bash
# Use incremental build (don't clean)
./gradlew build

# Or enable parallel execution
./gradlew clean build --parallel
```

### Dependency Resolution Issues

**Clear cache and rebuild:**
```bash
# Maven
rm -rf ~/.m2/repository
./mvnw clean compile

# Gradle
rm -rf ~/.gradle/caches
./gradlew clean build
```

---

## CI/CD Pipeline Examples

### GitHub Actions (Maven)

```yaml
name: Maven Build
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: ./mvnw clean verify
```

### GitHub Actions (Gradle)

```yaml
name: Gradle Build
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: ./gradlew clean build
```

### GitLab CI (Maven)

```yaml
build:
  image: maven:3.9.12-eclipse-temurin-21
  script:
    - ./mvnw clean verify
  artifacts:
    paths:
      - target/*.jar
```

### GitLab CI (Gradle)

```yaml
build:
  image: gradle:9.2.1-jdk21
  script:
    - ./gradlew clean build
  artifacts:
    paths:
      - build/libs/*.jar
```

---

## Performance Tuning

### Maven Performance

**Enable Maven Daemon:**
```bash
# Install Maven Daemon
brew install maven-daemon  # macOS
# or download from: https://mvnd.apache.org

# Use daemon for faster builds
mvnd clean verify
```

**Parallel Compilation:**
```bash
./mvnw clean verify -T 1C -DskipTests
```

**Concurrent Test Execution:**
```bash
./mvnw test -Dparallel=methods -DthreadCount=4
```

### Gradle Performance

**Enable Build Cache:**
```bash
# Cache enabled by default, or explicitly:
./gradlew build --build-cache
```

**Parallel Task Execution:**
```bash
./gradlew build --parallel --max-workers=4
```

**Incremental Builds:**
```bash
# Avoid clean when possible
./gradlew build  # Much faster than clean build
```

---

## Summary

### Quick Reference

| Goal | Maven Command | Gradle Command |
|------|---------------|-----------------|
| **Fast compile** | `./mvnw clean compile` | `./gradlew clean build -x test` |
| **Build for deployment** | `./mvnw clean package` | `./gradlew clean assemble` |
| **Full validation** | `./mvnw clean verify` | `./gradlew clean build` |
| **Run tests** | `./mvnw test` | `./gradlew test` |
| **Run application** | `./mvnw spring-boot:run` | `./gradlew bootRun` |
| **Incremental build** | `./mvnw package` | `./gradlew build` |

### Success Indicators

✅ **Maven Success:**
```
[INFO] BUILD SUCCESS
```

✅ **Gradle Success:**
```
BUILD SUCCESSFUL in X.XXs
```

✅ **Application Running:**
```
Started PetClinicApplication in X.XXXs using Java 21.0.x
```

---

**For detailed analysis, see:** [BUILD_VALIDATION_REPORT.md](BUILD_VALIDATION_REPORT.md)
