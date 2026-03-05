# Quick Start Guide - 10 Minutes to Running Benchmarks

This ultra-condensed guide gets you running the Spring PetClinic benchmark suite in under 10 minutes. For detailed information, refer to `SETUP.md`.

---

## Prerequisites (5 minutes)

### Minimum Installations
```bash
# Install Java 17 and 21
# Download from: https://adoptium.net/temurin/releases/

# Verify installation
java -version
# Output should show Java 17 or 21

# Install Git
git --version
```

### Optional: Docker (for external databases)
```bash
# Download from: https://www.docker.com/products/docker-desktop/
docker --version
```

---

## Clone and Setup (2 minutes)

```bash
# Clone repository
git clone https://github.com/spring-projects/spring-petclinic.git
cd spring-petclinic

# Verify directory structure
ls -la
# Should see: pom.xml, build.gradle, src/, mvnw, gradlew
```

---

## Build All Variants (3 minutes)

### Option A: Quick Build (Java 17 Baseline Only)
```bash
# Build Java 17 baseline with Maven (uses bundled wrapper)
./mvnw clean package -DskipTests -q

# Output:
# target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

### Option B: All Variants (with both Java versions)

**Set up Java versions** (one-time setup):
```bash
# macOS - automatic detection
/usr/libexec/java_home -v 17  # Note the path
/usr/libexec/java_home -v 21  # Note the path

# Windows/Linux - set environment variables
export JAVA_17_HOME=/path/to/jdk17
export JAVA_21_HOME=/path/to/jdk21
```

**Build all three variants**:
```bash
# Reset to Java 17
export JAVA_HOME=$JAVA_17_HOME
./mvnw clean package -DskipTests -q
mv target/spring-petclinic-4.0.0-SNAPSHOT.jar \
   target/spring-petclinic-java17.jar

# Switch to Java 21
export JAVA_HOME=$JAVA_21_HOME
./mvnw clean package -DskipTests -q
mv target/spring-petclinic-4.0.0-SNAPSHOT.jar \
   target/spring-petclinic-java21.jar

echo "All variants built!"
ls -lh target/spring-petclinic-java*.jar
```

---

## Run Application (< 1 minute)

### Java 17 Baseline (In-Memory H2 Database)
```bash
# Most basic: No database setup needed
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# With explicit memory settings
java -Xms512m -Xmx1g -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# Access application:
# Open browser: http://localhost:8080
```

### Java 21 Traditional
```bash
# Same command, just different JDK
export JAVA_HOME=$JAVA_21_HOME
java -Xms1g -Xmx2g -XX:+UseG1GC -jar target/spring-petclinic-java21.jar
```

### Java 21 with Virtual Threads
```bash
export JAVA_HOME=$JAVA_21_HOME
java -Xms512m -Xmx2g -jar target/spring-petclinic-java21.jar \
  --spring.profiles.active=vthreads

# Now using lightweight virtual threads!
```

---

## Quick Application Tests

### Health Check
```bash
# While app is running in another terminal:
curl http://localhost:8080/actuator/health

# Expected output: {"status":"UP"}
```

### Actuator Metrics
```bash
# View all available metrics
curl http://localhost:8080/actuator/metrics | jq .names[]

# Check thread count
curl http://localhost:8080/actuator/metrics/jvm.threads.live | jq

# Check memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq
```

### Web Interface
```bash
# Just open in browser
http://localhost:8080

# Find pets and owners
http://localhost:8080/owners/find
```

---

## Optional: Use External Databases

### MySQL (Containerized)

```bash
# Start MySQL container
docker compose up mysql -d

# Run application with MySQL profile
java -Xms1g -Xmx2g -jar target/spring-petclinic-java21.jar \
  --spring.profiles.active=mysql

# Stop container when done
docker compose down mysql
```

### PostgreSQL (Containerized)

```bash
# Start PostgreSQL container
docker compose up postgres -d

# Run application with PostgreSQL profile
java -Xms1g -Xmx2g -jar target/spring-petclinic-java21.jar \
  --spring.profiles.active=postgres

# Stop container when done
docker compose down postgres
```

---

## Quick Troubleshooting

| Problem | Solution |
|---------|----------|
| `java -version` shows Java 8 | `export JAVA_HOME=/path/to/jdk21` |
| Build fails with "compilation error" | Clear Maven cache: `rm -rf ~/.m2/repository` |
| Port 8080 already in use | Use different port: `java -jar app.jar --server.port=8081` |
| MySQL connection refused | Start container: `docker compose up mysql -d` |
| Out of memory | Increase heap: `java -Xmx4g -jar app.jar` |

---

## Compare Variants Side-by-Side

Run three instances simultaneously (in different terminals):

**Terminal 1: Java 17 Baseline**
```bash
java -Xms512m -Xmx1g -jar target/spring-petclinic-java17.jar
# Access: http://localhost:8080
```

**Terminal 2: Java 21 Traditional**
```bash
export JAVA_HOME=$JAVA_21_HOME
java -Xms1g -Xmx2g -XX:+UseG1GC -jar target/spring-petclinic-java21.jar \
  --server.port=8081
# Access: http://localhost:8081
```

**Terminal 3: Java 21 Virtual Threads**
```bash
export JAVA_HOME=$JAVA_21_HOME
java -Xms512m -Xmx2g -XX:+UseG1GC -jar target/spring-petclinic-java21.jar \
  --spring.profiles.active=vthreads --server.port=8082
# Access: http://localhost:8082
```

---

## Next Steps

1. **For detailed setup**: See `SETUP.md`
2. **For advanced config**: See `SETUP-ADVANCED.md`
3. **For benchmarking**: See (upcoming) `BENCHMARK-EXECUTION.md`

### Quick Performance Check

```bash
# Monitor while app is running
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/jvm.threads.live | jq'

# Capture heap dump
curl http://localhost:8080/actuator/heapdump > heap.hprof

# Get thread dump
curl http://localhost:8080/actuator/threaddump > threads.txt
```

---

**Time to Running App**: ~10 minutes
**Time to Benchmarking**: ~30 minutes (with full database setup)

For questions, refer to the Troubleshooting section in `SETUP.md`.
