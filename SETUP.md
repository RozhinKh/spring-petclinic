# Spring PetClinic Benchmark Suite - Setup Guide

This comprehensive guide covers the setup, build, and environment configuration for the Spring PetClinic benchmark suite across three Java variants: Java 17 baseline, Java 21 variant A (traditional), and Java 21 variant B (virtual threads).

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Build Instructions](#build-instructions)
3. [Environment Setup](#environment-setup)
4. [Configuration Examples](#configuration-examples)
5. [JVM Configuration](#jvm-configuration)
6. [IDE Setup](#ide-setup)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

All three benchmark variants require the following tools to be installed on your system.

#### Java Development Kits (JDK)

You must install **both JDK 17 and JDK 21** to build and run all three variants.

**Java 17 (Baseline variant)**
- **Minimum Version**: Java 17 (LTS)
- **Recommended**: Java 17.0.8 or later
- **Download**: https://adoptium.net/temurin/releases/?version=17
  - Select JDK 17 (LTS) for your operating system
  - Choose the .msi (Windows), .pkg (macOS), or .tar.gz (Linux)
- **Installation**: Follow the installer prompts or extract to a directory
- **Verification**: 
  ```bash
  java -version
  # Expected: openjdk version "17.x.x"
  ```

**Java 21 (Variant A & B)**
- **Minimum Version**: Java 21 (LTS)
- **Recommended**: Java 21.0.1 or later
- **Download**: https://adoptium.net/temurin/releases/?version=21
  - Select JDK 21 (LTS) for your operating system
  - Choose the .msi (Windows), .pkg (macOS), or .tar.gz (Linux)
- **Installation**: Follow the installer prompts or extract to a directory
- **Verification**: 
  ```bash
  java -version
  # Expected: openjdk version "21.x.x"
  ```

#### Build Tools

**Maven 3.9+**
- **Minimum Version**: Maven 3.9.0
- **Recommended**: Maven 3.9.5 or later
- **Download**: https://maven.apache.org/download.cgi
  - Choose "Binary zip archive" or "Binary tar.gz archive"
- **Installation** (Unix/Linux/macOS):
  ```bash
  # Extract archive
  tar -xzf apache-maven-3.9.5-bin.tar.gz
  
  # Add to PATH (in ~/.bashrc, ~/.zshrc, or equivalent)
  export PATH=$PATH:/path/to/apache-maven-3.9.5/bin
  
  # Verify
  mvn -version
  # Expected: Apache Maven 3.9.5
  ```
- **Installation** (Windows):
  - Extract the archive to `C:\Maven` or similar
  - Add `C:\Maven\apache-maven-3.9.5\bin` to your PATH environment variable
  - Open new Command Prompt and verify: `mvn -version`
- **Note**: The project includes Maven wrapper (`mvnw`) which auto-downloads Maven, so this is optional if using the wrapper

**Gradle 8.0+**
- **Minimum Version**: Gradle 8.0
- **Recommended**: Gradle 8.4 or later
- **Download**: https://gradle.org/releases/
  - Choose "Binary-only" for your operating system
- **Installation** (Unix/Linux/macOS):
  ```bash
  # Extract archive
  unzip gradle-8.4-bin.zip
  
  # Add to PATH (in ~/.bashrc, ~/.zshrc, or equivalent)
  export PATH=$PATH:/path/to/gradle-8.4/bin
  
  # Verify
  gradle -version
  # Expected: Gradle 8.4
  ```
- **Installation** (Windows):
  - Extract the archive to `C:\Gradle` or similar
  - Add `C:\Gradle\gradle-8.4\bin` to your PATH environment variable
  - Open new Command Prompt and verify: `gradle -version`
- **Note**: The project includes Gradle wrapper (`gradlew`) which auto-downloads Gradle, so this is optional if using the wrapper

#### Git

- **Minimum Version**: Git 2.30+
- **Download**: https://git-scm.com/downloads
- **Installation**: Follow platform-specific installer
- **Verification**: 
  ```bash
  git --version
  # Expected: git version 2.x.x
  ```

### Optional Software (for Database Backends)

If you want to use external databases instead of the default H2 in-memory database:

#### MySQL 8.0+
- **Minimum Version**: MySQL 8.0
- **Recommended**: MySQL 9.5 (as per docker-compose.yml)
- **Download**: https://dev.mysql.com/downloads/mysql/
- **For Docker-based setup** (Recommended):
  ```bash
  # Verify Docker is installed
  docker --version
  # Expected: Docker version 20.10+ or Docker Desktop latest
  
  # MySQL 9.5 container can be started via docker-compose or:
  docker run -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=petclinic \
    -e MYSQL_USER=petclinic -e MYSQL_PASSWORD=petclinic \
    -p 3306:3306 mysql:9.5
  ```
- **Download Docker**: https://www.docker.com/products/docker-desktop/

#### PostgreSQL 14+
- **Minimum Version**: PostgreSQL 14
- **Recommended**: PostgreSQL 18.1 (as per docker-compose.yml)
- **Download**: https://www.postgresql.org/download/
- **For Docker-based setup** (Recommended):
  ```bash
  # Verify Docker is installed
  docker --version
  
  # PostgreSQL 18.1 container can be started via docker-compose or:
  docker run -e POSTGRES_PASSWORD=petclinic -e POSTGRES_USER=petclinic \
    -e POSTGRES_DB=petclinic -p 5432:5432 postgres:18.1
  ```

#### Docker & Docker Compose
- **Minimum Docker**: 20.10+
- **Minimum Docker Compose**: 1.29+
- **Download**: https://www.docker.com/products/docker-desktop/
- **Verification**: 
  ```bash
  docker --version
  docker compose version  # Note: newer versions use `docker compose` not `docker-compose`
  ```

### Optional: Performance Analysis Tools

#### JMeter 5.x (for load testing)
- **Minimum Version**: JMeter 5.4+
- **Download**: https://jmeter.apache.org/download_jmeter.cgi
- **Installation**: Download binary archive, extract, run `bin/jmeter.sh` (Unix) or `bin\jmeter.bat` (Windows)
- **Note**: Optional for benchmarking suite

#### Gatling 3.x (for load testing)
- **Minimum Version**: Gatling 3.8+
- **Download**: https://gatling.io/open-source/
- **Installation**: Download, extract, use `bin/gatling.sh` (Unix) or `bin\gatling.bat` (Windows)
- **Note**: Optional for benchmarking suite

### System Requirements

- **Operating System**: Windows 10+, macOS 10.15+, Linux (Ubuntu 18.04+, CentOS 8+, or equivalent)
- **RAM**: Minimum 4GB, recommended 8GB+ (especially for running databases + application)
- **Disk Space**: 5GB minimum (accounts for JDK, Maven/Gradle caches, databases, and compiled artifacts)
- **Network**: Required for initial dependency downloads from Maven Central

---

## Build Instructions

### Preliminary Steps (All Variants)

Before building any variant, you must clone the repository:

```bash
# Clone the repository
git clone https://github.com/spring-projects/spring-petclinic.git
cd spring-petclinic

# Verify you're on main branch
git branch
git status
```

### Setting Java Home for Multiple JDK Versions

Since you need both Java 17 and 21, configure them as follows:

**Unix/Linux/macOS - Add to ~/.bashrc, ~/.zshrc, or ~/.bash_profile:**
```bash
# Set JAVA_HOME for different versions
export JAVA_17_HOME=/path/to/jdk-17  # e.g., /usr/libexec/java_home -v 17 (macOS)
export JAVA_21_HOME=/path/to/jdk-21  # e.g., /usr/libexec/java_home -v 21 (macOS)

# Default to Java 17
export JAVA_HOME=$JAVA_17_HOME

# Function to switch Java versions
setjava17() { export JAVA_HOME=$JAVA_17_HOME; echo "Switched to Java 17"; java -version; }
setjava21() { export JAVA_HOME=$JAVA_21_HOME; echo "Switched to Java 21"; java -version; }
```

**Windows - Edit Environment Variables:**
1. Press `Win+X` and select "System"
2. Click "Advanced system settings"
3. Click "Environment Variables"
4. Create new User variables:
   - `JAVA_17_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-17.x.x`
   - `JAVA_21_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-21.x.x`
   - `JAVA_HOME` = `%JAVA_17_HOME%` (default)
5. Add `%JAVA_HOME%\bin` to your PATH
6. Open new Command Prompt to apply changes

**Switching Java versions in Command Prompt (Windows):**
```batch
REM Switch to Java 21
set JAVA_HOME=%JAVA_21_HOME%
java -version

REM Switch back to Java 17
set JAVA_HOME=%JAVA_17_HOME%
java -version
```

### Variant 1: Java 17 Baseline Build

The baseline variant uses the default configuration with Java 17.

#### Maven Build (Java 17 Baseline)

```bash
# Ensure JAVA_HOME points to Java 17
export JAVA_HOME=$JAVA_17_HOME  # Unix/Linux/macOS
# OR set JAVA_HOME=%JAVA_17_HOME% (Windows)

# Clean and build
./mvnw clean package

# Expected artifacts at:
# - target/spring-petclinic-4.0.0-SNAPSHOT.jar (executable JAR)
# - target/classes/ (compiled classes)

# Verification (should show Java 17):
./mvnw -version
```

**Full Maven Build with Integration Tests:**
```bash
# Build including integration tests (requires Docker/docker-compose for database tests)
./mvnw clean verify -DskipITs=false

# If you only want to package without tests:
./mvnw clean package -DskipTests
```

**Build Output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: XX.XXs
[INFO] Finished at: YYYY-MM-DDTHH:MM:SSZ
[INFO] Final Memory: XXMb/XXXMb
```

#### Gradle Build (Java 17 Baseline)

```bash
# Ensure JAVA_HOME points to Java 17
export JAVA_HOME=$JAVA_17_HOME  # Unix/Linux/macOS
# OR set JAVA_HOME=%JAVA_17_HOME% (Windows)

# Clean and build
./gradlew clean build

# Expected artifacts at:
# - build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar (executable JAR)
# - build/classes/java/main/ (compiled classes)

# Verification (should show Java 17):
./gradlew -version
```

**Full Gradle Build with Tests:**
```bash
# Build including tests
./gradlew clean build

# If you only want to package without tests:
./gradlew clean build -x test
```

#### Running Java 17 Baseline Application

After building, run the application:

**Maven:**
```bash
# Using the built JAR
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# Or using Maven plugin (requires H2 database)
./mvnw spring-boot:run
```

**Gradle:**
```bash
# Using the built JAR
java -jar build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar

# Or using Gradle plugin (requires H2 database)
./gradlew bootRun
```

**Expected Console Output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_|\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::        (v4.0.1)

Started PetClinicApplication in X.XXX seconds (JVM running for X.XXX)
Tomcat started on port(s): 8080 (http)
```

**Access the application:**
- Open browser to `http://localhost:8080`
- H2 console available at `http://localhost:8080/h2-console`

---

### Variant 2: Java 21 Variant A (Traditional Build)

Variant A uses Java 21 with traditional threading model (no virtual threads).

#### Maven Build (Java 21 Variant A)

```bash
# Ensure JAVA_HOME points to Java 21
export JAVA_HOME=$JAVA_21_HOME  # Unix/Linux/macOS
# OR set JAVA_HOME=%JAVA_21_HOME% (Windows)

# Clean and build with Maven
./mvnw clean package

# Expected artifacts at:
# - target/spring-petclinic-4.0.0-SNAPSHOT.jar (executable JAR for Java 21)
# - target/classes/ (compiled classes with Java 21 features)

# Verification (should show Java 21):
./mvnw -version
```

**Maven Build Profile (if using profile-based variants):**
```bash
# Optional: if you have a 'java21-traditional' profile
export JAVA_HOME=$JAVA_21_HOME
./mvnw clean package -P java21-traditional
```

**Full Maven Build with Integration Tests:**
```bash
export JAVA_HOME=$JAVA_21_HOME
./mvnw clean verify -DskipITs=false
```

#### Gradle Build (Java 21 Variant A)

```bash
# Ensure JAVA_HOME points to Java 21
export JAVA_HOME=$JAVA_21_HOME  # Unix/Linux/macOS
# OR set JAVA_HOME=%JAVA_21_HOME% (Windows)

# Clean and build
./gradlew clean build

# Expected artifacts at:
# - build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar (executable JAR for Java 21)
# - build/classes/java/main/ (compiled classes with Java 21 features)

# Verification (should show Java 21):
./gradlew -version
```

**Full Gradle Build with Tests:**
```bash
export JAVA_HOME=$JAVA_21_HOME
./gradlew clean build
```

#### Running Java 21 Variant A Application

```bash
# Using the built JAR (Java 21)
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# With custom JVM arguments (see JVM Configuration section)
java -Xms512m -Xmx2g \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

---

### Variant 3: Java 21 Variant B (Virtual Threads Build)

Variant B uses Java 21 with virtual threads enabled (Project Loom).

#### Enabling Virtual Threads

To use virtual threads in Spring Boot 4.0.1 (or later), you need to configure the web server to use virtual threads. Create or modify a configuration file:

**Option 1: application-vthreads.properties**

Create `src/main/resources/application-vthreads.properties`:
```properties
# Virtual threads configuration for Java 21+
server.tomcat.threads.max=10000
server.tomcat.virtual-threads.enabled=true
```

**Option 2: application.yml**

If your project uses YAML configuration, add to `application.yml`:
```yaml
server:
  tomcat:
    threads:
      max: 10000
    virtual-threads:
      enabled: true
```

#### Maven Build (Java 21 Variant B with Virtual Threads)

```bash
# Ensure JAVA_HOME points to Java 21
export JAVA_HOME=$JAVA_21_HOME  # Unix/Linux/macOS
# OR set JAVA_HOME=%JAVA_21_HOME% (Windows)

# Clean and build
./mvnw clean package

# Expected artifacts:
# - target/spring-petclinic-4.0.0-SNAPSHOT.jar (compiled with Java 21)
# - Uses same build as Variant A, differentiation is in configuration
```

#### Gradle Build (Java 21 Variant B with Virtual Threads)

```bash
# Ensure JAVA_HOME points to Java 21
export JAVA_HOME=$JAVA_21_HOME

# Clean and build
./gradlew clean build

# Expected artifacts at:
# - build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar (compiled with Java 21)
```

#### Running Java 21 Variant B with Virtual Threads

```bash
# Run with virtual threads profile (requires application-vthreads.properties)
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=vthreads

# Or use environment variable
export SPRING_PROFILES_ACTIVE=vthreads
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# Or with custom JVM arguments
java -Xms512m -Xmx2g \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+UseG1GC \
  -Dspring.profiles.active=vthreads \
  -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

**Virtual Threads Verification:**

Check that virtual threads are enabled by examining the logs:
```
# Look for messages indicating thread pool configuration
# You can also create a test endpoint or examine active threads
```

---

### Build Artifacts Summary

After successful builds, you should have:

| Variant | Path (Maven) | Path (Gradle) | Java Version |
|---------|--------------|---------------|--------------|
| Java 17 Baseline | `target/spring-petclinic-4.0.0-SNAPSHOT.jar` | `build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar` | 17 |
| Java 21 Variant A | `target/spring-petclinic-4.0.0-SNAPSHOT.jar` | `build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar` | 21 |
| Java 21 Variant B | `target/spring-petclinic-4.0.0-SNAPSHOT.jar` | `build/libs/spring-petclinic-4.0.0-SNAPSHOT.jar` | 21 (vthreads) |

---

## Environment Setup

### Database Configuration

The application supports three database backends: H2 (default, in-memory), MySQL, and PostgreSQL.

#### H2 In-Memory Database (Default)

No setup required. The database is initialized automatically on application startup.

**Configuration:**
- File: No additional configuration needed
- Default URL: `jdbc:h2:mem:<random-uuid>`
- H2 Console: `http://localhost:8080/h2-console`

**Steps to verify:**
1. Start the application without any database profile
2. Open `http://localhost:8080/h2-console` in browser
3. Click "Connect" (default connection will be selected)
4. You should see PetClinic tables: `owner`, `pet`, `pet_type`, `visit`, etc.

#### MySQL 8.0+ (Containerized - Recommended)

Using Docker and Docker Compose is the recommended approach.

**Step 1: Start MySQL Container via Docker Compose**

```bash
# From the project root directory
docker compose up mysql

# Expected output:
# mysql  | 2024-XX-XX HH:MM:SS 0 [Note] /usr/sbin/mysqld: ready for connections.
```

**Step 2: Verify MySQL is Running**

```bash
# Test connection (if mysql-client installed)
mysql -h localhost -u petclinic -p petclinic -e "SELECT 1;"
# Enter password: petclinic

# Or test with Docker
docker exec <container_id> mysql -u petclinic -p petclinic -e "SELECT 1;"
```

**Step 3: Build and Run Application with MySQL Profile**

```bash
# Build first
./mvnw clean package -DskipTests

# Run with MySQL profile
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=mysql
```

**MySQL docker-compose.yml Configuration:**
```yaml
mysql:
  image: mysql:9.5
  ports:
    - "3306:3306"
  environment:
    - MYSQL_ROOT_PASSWORD=root
    - MYSQL_USER=petclinic
    - MYSQL_PASSWORD=petclinic
    - MYSQL_DATABASE=petclinic
  volumes:
    - "./conf.d:/etc/mysql/conf.d:ro"
```

**Connection Details:**
- Host: `localhost`
- Port: `3306`
- Database: `petclinic`
- Username: `petclinic`
- Password: `petclinic`
- Root Password: `root`

#### PostgreSQL 14+ (Containerized - Recommended)

Using Docker and Docker Compose is the recommended approach.

**Step 1: Start PostgreSQL Container via Docker Compose**

```bash
# From the project root directory
docker compose up postgres

# Expected output:
# postgres  | LOG:  database system is ready to accept connections
```

**Step 2: Verify PostgreSQL is Running**

```bash
# Test connection (if psql installed)
psql -h localhost -U petclinic -d petclinic -c "SELECT 1;"
# Enter password: petclinic

# Or test with Docker
docker exec <container_id> psql -U petclinic -d petclinic -c "SELECT 1;"
```

**Step 3: Build and Run Application with PostgreSQL Profile**

```bash
# Build first
./mvnw clean package -DskipTests

# Run with PostgreSQL profile
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=postgres
```

**PostgreSQL docker-compose.yml Configuration:**
```yaml
postgres:
  image: postgres:18.1
  ports:
    - "5432:5432"
  environment:
    - POSTGRES_PASSWORD=petclinic
    - POSTGRES_USER=petclinic
    - POSTGRES_DB=petclinic
```

**Connection Details:**
- Host: `localhost`
- Port: `5432`
- Database: `petclinic`
- Username: `petclinic`
- Password: `petclinic`

#### Manual Database Installation (Alternative)

If not using Docker, manually install MySQL or PostgreSQL:

**MySQL Manual Installation:**
1. Download from https://dev.mysql.com/downloads/mysql/
2. Follow platform-specific installation instructions
3. Create database and user:
   ```sql
   CREATE DATABASE petclinic;
   CREATE USER 'petclinic'@'localhost' IDENTIFIED BY 'petclinic';
   GRANT ALL PRIVILEGES ON petclinic.* TO 'petclinic'@'localhost';
   FLUSH PRIVILEGES;
   ```

**PostgreSQL Manual Installation:**
1. Download from https://www.postgresql.org/download/
2. Follow platform-specific installation instructions
3. Create database and user:
   ```sql
   CREATE DATABASE petclinic;
   CREATE USER petclinic WITH PASSWORD 'petclinic';
   ALTER ROLE petclinic WITH CREATEDB;
   GRANT ALL PRIVILEGES ON DATABASE petclinic TO petclinic;
   ```

### Database Profile Activation

Configure which database the application uses:

**Option 1: Command-line argument**
```bash
java -jar application.jar --spring.profiles.active=mysql
java -jar application.jar --spring.profiles.active=postgres
# No profile defaults to H2
```

**Option 2: Environment variable**
```bash
export SPRING_PROFILES_ACTIVE=mysql
java -jar application.jar

# Windows:
set SPRING_PROFILES_ACTIVE=postgres
java -jar application.jar
```

**Option 3: JVM property (not recommended for Spring Boot)**
```bash
java -Dspring.profiles.active=mysql -jar application.jar
```

---

## Configuration Examples

### application.properties Profile Examples

#### Variant 1: Java 17 Baseline with H2

**File: application.properties (default)**
```properties
# H2 in-memory database configuration
database=h2
spring.sql.init.schema-locations=classpath*:db/${database}/schema.sql
spring.sql.init.data-locations=classpath*:db/${database}/data.sql

# Web
spring.thymeleaf.mode=HTML

# JPA
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl
spring.jpa.properties.hibernate.default_batch_fetch_size=16

# Internationalization
spring.messages.basename=messages/messages

# Actuator
management.endpoints.web.exposure.include=*

# Logging
logging.level.org.springframework=INFO

# Cache
spring.cache.type=caffeine

# Maximum time static resources should be cached
spring.web.resources.cache.cachecontrol.max-age=12h
```

#### Variant 2: Java 21 Traditional with MySQL

**File: application-mysql.properties**
```properties
# MySQL database configuration
database=mysql
spring.datasource.url=${MYSQL_URL:jdbc:mysql://localhost:3306/petclinic}
spring.datasource.username=${MYSQL_USER:petclinic}
spring.datasource.password=${MYSQL_PASS:petclinic}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# SQL initialization
spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath*:db/${database}/schema.sql
spring.sql.init.data-locations=classpath*:db/${database}/data.sql

# Connection pooling (HikariCP - Spring Boot default)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000

# JPA/Hibernate
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.default_batch_fetch_size=16
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.jdbc.fetch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true

# Web
spring.thymeleaf.mode=HTML

# Actuator
management.endpoints.web.exposure.include=*
management.endpoints.web.base-path=/actuator

# Logging
logging.level.org.springframework=INFO
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql=TRACE

# Cache
spring.cache.type=caffeine

# Maximum time static resources should be cached
spring.web.resources.cache.cachecontrol.max-age=12h
```

#### Variant 3: Java 21 Virtual Threads with PostgreSQL

**File: application-postgres.properties**
```properties
# PostgreSQL database configuration
database=postgres
spring.datasource.url=${POSTGRES_URL:jdbc:postgresql://localhost:5432/petclinic}
spring.datasource.username=${POSTGRES_USER:petclinic}
spring.datasource.password=${POSTGRES_PASS:petclinic}
spring.datasource.driver-class-name=org.postgresql.Driver

# SQL initialization
spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath*:db/${database}/schema.sql
spring.sql.init.data-locations=classpath*:db/${database}/data.sql

# Connection pooling (HikariCP)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000

# JPA/Hibernate
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.default_batch_fetch_size=16
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.jdbc.fetch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true

# Web
spring.thymeleaf.mode=HTML

# Virtual Threads (Java 21+)
server.tomcat.threads.max=10000
server.tomcat.virtual-threads.enabled=true

# Actuator
management.endpoints.web.exposure.include=*
management.endpoints.web.base-path=/actuator

# Logging
logging.level.org.springframework=INFO
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql=TRACE

# Cache
spring.cache.type=caffeine

# Maximum time static resources should be cached
spring.web.resources.cache.cachecontrol.max-age=12h
```

**File: application-vthreads.properties (Virtual Threads specific)**
```properties
# Virtual Threads Configuration for Java 21+
# This profile enables Tomcat virtual threads

# Tomcat virtual threads settings
server.tomcat.threads.max=10000
server.tomcat.virtual-threads.enabled=true

# With virtual threads, we can handle many more concurrent connections
# Set a larger worker thread count since they're lightweight
server.tomcat.threads.min-spare=10

# Connection pooling can be more aggressive with virtual threads
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=10
```

---

## JVM Configuration

### Memory Configuration

Appropriate JVM memory settings depend on your benchmark scenario.

#### Development/Testing Configuration

Suitable for development work and basic testing:

```bash
# Baseline configuration for development
java -Xms512m -Xmx1g \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar

# Explanation:
# -Xms512m   : Initial heap size of 512 MB
# -Xmx1g     : Maximum heap size of 1 GB
```

#### Benchmark Configuration (Light Load)

For running benchmarks with moderate load:

```bash
# Light benchmark configuration
java -Xms1g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:InitiatingHeapOccupancyPercent=35 \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar

# Explanation:
# -Xms1g                           : Initial heap size of 1 GB
# -Xmx2g                           : Maximum heap size of 2 GB
# -XX:+UseG1GC                     : Use G1 garbage collector (recommended for Java 17+)
# -XX:MaxGCPauseMillis=200         : Target max GC pause time of 200ms
# -XX:InitiatingHeapOccupancyPercent=35 : Start concurrent GC at 35% heap occupancy
```

#### Benchmark Configuration (Heavy Load)

For production-like benchmarks with high throughput:

```bash
# Heavy benchmark configuration
java -Xms4g -Xmx8g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:InitiatingHeapOccupancyPercent=30 \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:G1SummarizeRSetStatsPeriod=86400 \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar

# Explanation:
# -Xms4g, -Xmx8g                  : Large heap allocation
# -XX:G1NewSizePercent=30         : Young generation at 30% of total
# -XX:G1MaxNewSizePercent=40      : Young generation at max 40% of total
# -XX:+ParallelRefProcEnabled     : Parallel processing of weak/soft/phantom references
```

#### Virtual Threads Configuration

When using virtual threads (Java 21 Variant B), JVM memory settings can be lower due to thread efficiency:

```bash
# Virtual threads optimized configuration
java -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=150 \
  -Dspring.profiles.active=vthreads \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar

# With virtual threads, we typically use:
# - Lower initial memory (512m vs 1g)
# - Similar max memory (2g appropriate for benchmark workloads)
# - Relaxed GC pause target due to thread efficiency
```

### Garbage Collector Configuration

#### G1GC (Recommended for Java 17+)

G1 (Garbage First) is the default GC in Java 17+ and is recommended for most workloads:

```bash
# Standard G1GC configuration
java -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:InitiatingHeapOccupancyPercent=35 \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:gc-%t.log \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

#### ZGC (Low-Latency Alternative - Java 21)

For ultra-low pause times (if latency-sensitive):

```bash
# ZGC configuration (Java 21+)
java -Xms2g -Xmx4g \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:gc-%t.log \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar

# Note: ZGC requires more memory overhead but provides consistent <1ms pauses
```

### Java Flight Recorder (JFR) Configuration

JFR provides detailed performance profiling without significant overhead (< 1% overhead).

#### Continuous JFR Recording

Record all events with default settings:

```bash
# Basic JFR continuous recording
java -Xms2g -Xmx4g \
  -XX:+UnlockCommercialFeatures \
  -XX:+FlightRecorder \
  -XX:FlightRecorderOptions=stackdepth=512 \
  -XX:StartFlightRecording=filename=recording.jfr,maxsize=1g,maxage=12h \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar

# After running, analyze with:
# jdk.jcmd -l  # Find the PID
# jdk.jcmd <PID> JFR.dump filename=output.jfr
# # Open output.jfr in JDK Mission Control
```

#### Profiling-Specific JFR Configuration

For detailed performance analysis:

```bash
# JFR with profiling settings
java -Xms2g -Xmx4g \
  -XX:+UnlockCommercialFeatures \
  -XX:+FlightRecorder \
  -XX:FlightRecorderOptions=stackdepth=1024,samplingrate=97 \
  -XX:StartFlightRecording=\
name=benchmark,\
filename=jfr/recording.jfr,\
maxsize=2g,\
maxage=24h,\
dumponexit=true,\
disk=true \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

### Actuator Endpoint Configuration

Spring Boot Actuator provides runtime metrics and diagnostics:

**application.properties:**
```properties
# Enable all actuator endpoints
management.endpoints.web.exposure.include=*

# Or selectively expose specific endpoints:
# management.endpoints.web.exposure.include=health,metrics,env,loggers,threaddump,heapdump

# Base path for actuator endpoints
management.endpoints.web.base-path=/actuator

# Endpoint-specific settings
management.endpoint.health.show-details=always
management.endpoint.health.show-components=always

# Metrics
management.metrics.export.prometheus.enabled=true
management.metrics.enable.jvm=true
management.metrics.enable.process=true
management.metrics.enable.system=true

# Request logging
management.trace.http.enabled=true
```

**Useful Actuator Endpoints:**
- `/actuator/health` - Application health status
- `/actuator/metrics` - Available metrics list
- `/actuator/metrics/{name}` - Specific metric value
- `/actuator/env` - Environment properties
- `/actuator/loggers` - Logger configuration
- `/actuator/threaddump` - Thread dump (useful for analyzing threads vs virtual threads)
- `/actuator/heapdump` - Heap dump for memory analysis
- `/actuator/prometheus` - Prometheus format metrics (if enabled)

**Accessing Actuator Endpoints:**
```bash
# Health check
curl http://localhost:8080/actuator/health | jq

# View all available metrics
curl http://localhost:8080/actuator/metrics | jq

# Get specific metric
curl http://localhost:8080/actuator/metrics/jvm.threads.live | jq

# Get thread information
curl http://localhost:8080/actuator/threaddump > threaddump.txt

# Download heap dump
curl http://localhost:8080/actuator/heapdump > heapdump.hprof
```

### Complete JVM Configuration Examples by Scenario

#### Development (Single Developer Machine)

```bash
java -Xms512m -Xmx1g \
  -XX:+UseG1GC \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

#### Testing (CI/CD Pipeline)

```bash
java -Xms1g -Xmx2g \
  -XX:+UseG1GC \
  -XX:+PrintGCDetails \
  -Xloggc:build/gc.log \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

#### Benchmark - Throughput Focus

```bash
java -Xms4g -Xmx8g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:InitiatingHeapOccupancyPercent=30 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UnlockCommercialFeatures \
  -XX:+FlightRecorder \
  -XX:FlightRecorderOptions=stackdepth=512 \
  -XX:StartFlightRecording=filename=recording-throughput.jfr,maxsize=2g,disk=true \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

#### Benchmark - Latency Focus

```bash
java -Xms2g -Xmx4g \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:+UnlockCommercialFeatures \
  -XX:+FlightRecorder \
  -XX:StartFlightRecording=filename=recording-latency.jfr,maxsize=1g,disk=true \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

#### Benchmark - Virtual Threads (Java 21)

```bash
java -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=150 \
  -XX:+UnlockCommercialFeatures \
  -XX:+FlightRecorder \
  -XX:StartFlightRecording=filename=recording-vthreads.jfr,maxsize=1g,disk=true \
  -Dspring.profiles.active=vthreads \
  -jar spring-petclinic-4.0.0-SNAPSHOT.jar
```

---

## IDE Setup

### IntelliJ IDEA

**Prerequisites:**
- IntelliJ IDEA 2023.3+ (Community or Ultimate)
- JDK 17+ configured in IntelliJ

**Steps:**

1. **Open Project:**
   - File → Open
   - Select the `spring-petclinic` directory (where `pom.xml` is located)
   - Click "Open"

2. **Configure JDK Versions:**
   - File → Project Structure → Project
   - Click "SDK" dropdown → Add SDK → JDK
   - Select JDK 17 path (from Prerequisites section)
   - Repeat to add JDK 21
   - Set Project SDK to JDK 17 (for baseline)

3. **Maven Configuration:**
   - File → Settings (Preferences on macOS) → Build, Execution, Deployment → Build Tools → Maven
   - Verify "Maven home directory" points to Maven 3.9+
   - Check "Override user settings file" and "Override local repository" if needed
   - Click "OK"

4. **Load Maven Project:**
   - IntelliJ should auto-detect pom.xml and show "Load Maven changes" notification
   - Or: Maven panel (right side) → Right-click project → Reimport

5. **Create Run Configuration for Java 17:**
   - Run → Edit Configurations
   - Click "+" → Application
   - Name: "PetClinic - Java 17"
   - Main class: `org.springframework.samples.petclinic.PetClinicApplication`
   - JDK: Select your Java 17 JDK
   - Environment variables: Leave empty for H2 default
   - Click "OK"

6. **Create Run Configuration for Java 21 (Variant A):**
   - Repeat step 5 with:
   - Name: "PetClinic - Java 21 Traditional"
   - JDK: Select your Java 21 JDK
   - Environment variables: Leave empty

7. **Create Run Configuration for Java 21 Virtual Threads (Variant B):**
   - Repeat step 5 with:
   - Name: "PetClinic - Java 21 Virtual Threads"
   - JDK: Select your Java 21 JDK
   - Program arguments: `--spring.profiles.active=vthreads`

8. **Run Application:**
   - Select desired run configuration from dropdown
   - Click Run button (▶) or press Shift+F10

9. **Build Project:**
   - Build → Build Project (Ctrl+F9)
   - Or Build → Rebuild Project for clean rebuild

10. **View Logs:**
    - View → Tool Windows → Run (or click Run tab at bottom)
    - Logs appear in the console

**Useful IntelliJ Features for Benchmarking:**
- Terminal (Alt+F12) - Run command-line tools
- Profiler - Run → Profile (shows CPU, memory, threads)
- Debugger - Set breakpoints and step through code
- Database tool - Connect to external MySQL/PostgreSQL
- HTTP Client - Test REST endpoints (Ctrl+Shift+A → "HTTP Client")

### Eclipse IDE & Spring Tools Suite (STS)

**Prerequisites:**
- Eclipse 2023.09+ or Spring Tools Suite 4.18+
- JDK 17+ installed

**Steps:**

1. **Import Project:**
   - File → Import
   - Select "Existing Maven Projects"
   - Browse to spring-petclinic directory
   - Click "Finish"

2. **Configure Multiple JDKs:**
   - Window → Preferences (Eclipse → Preferences on macOS)
   - Java → Installed JREs
   - Click "Add..."
   - Select JDK 17, click "Next", then "Finish"
   - Repeat for JDK 21

3. **Set Default JRE:**
   - Window → Preferences → Java → Installed JREs
   - Check JDK 17 as default
   - Click "Apply and Close"

4. **Configure Project JDK:**
   - Right-click project → Properties
   - Project Facets → Runtimes
   - Select appropriate runtime (or add new one with JDK 17)

5. **Create Run Configuration (Java 17):**
   - Run → Run Configurations
   - Right-click "Java Application" → New
   - Name: "PetClinic - Java 17"
   - Project: spring-petclinic
   - Main class: `org.springframework.samples.petclinic.PetClinicApplication`
   - Arguments tab → VM arguments: (empty for H2)
   - Run

6. **Create Run Configuration (Java 21 Variant A):**
   - Repeat step 5:
   - Name: "PetClinic - Java 21 Traditional"
   - Set JRE to Java 21

7. **Create Run Configuration (Java 21 Virtual Threads):**
   - Repeat step 5:
   - Name: "PetClinic - Java 21 Virtual Threads"
   - Program arguments: `--spring.profiles.active=vthreads`
   - Set JRE to Java 21

8. **Build:**
   - Project → Build Project (Ctrl+B)
   - Or Clean → Select project → OK for clean build

9. **View Console:**
   - Window → Show View → Console
   - Output appears in Console tab at bottom

**Useful Eclipse Features:**
- Debug mode - Right-click → Debug As → Java Application
- Test framework integration - For running unit tests
- Git integration - Team → Commit, Push, Pull
- External tools - Configure Maven, Gradle commands

### VS Code

**Prerequisites:**
- VS Code 1.75+
- Extension Pack for Java (from Microsoft) installed
- JDK 17+ installed

**Steps:**

1. **Install Extensions:**
   - Open Extensions (Ctrl+Shift+X)
   - Search "Extension Pack for Java" → Install
   - This includes Language Support for Java, Debugger for Java, Test Runner for Java, Maven for Java, Visual Studio IntelliCode

2. **Open Project Folder:**
   - File → Open Folder
   - Select spring-petclinic directory
   - Trust workspace when prompted

3. **Configure JDKs:**
   - Ctrl+Shift+A → "Java: Configure Java Runtime"
   - Add JDK 17 and JDK 21 paths
   - Set default to Java 17

4. **Maven Auto-Detection:**
   - VS Code should auto-detect pom.xml
   - Check Maven explorer on left sidebar
   - If not visible: Ctrl+Shift+A → "Maven: Show explorer"

5. **Run/Debug Configuration (.vscode/launch.json):**

   Create `.vscode/launch.json`:
   ```json
   {
     "version": "0.2.0",
     "configurations": [
       {
         "name": "PetClinic - Java 17",
         "type": "java",
         "name": "Java 17 Baseline",
         "request": "launch",
         "mainClass": "org.springframework.samples.petclinic.PetClinicApplication",
         "projectName": "spring-petclinic",
         "cwd": "${workspaceFolder}",
         "console": "integratedTerminal"
       },
       {
         "name": "PetClinic - Java 21 Traditional",
         "type": "java",
         "name": "Java 21 Traditional",
         "request": "launch",
         "mainClass": "org.springframework.samples.petclinic.PetClinicApplication",
         "projectName": "spring-petclinic",
         "cwd": "${workspaceFolder}",
         "console": "integratedTerminal"
       },
       {
         "name": "PetClinic - Java 21 Virtual Threads",
         "type": "java",
         "name": "Java 21 Virtual Threads",
         "request": "launch",
         "mainClass": "org.springframework.samples.petclinic.PetClinicApplication",
         "projectName": "spring-petclinic",
         "args": "--spring.profiles.active=vthreads",
         "cwd": "${workspaceFolder}",
         "console": "integratedTerminal"
       }
     ]
   }
   ```

6. **Build and Run:**
   - Maven explorer → Plugins → spring-boot → spring-boot:run
   - Or use Terminal: `./mvnw spring-boot:run`
   - Or click Run configuration dropdown and select configuration
   - Click Play (▶) button to run

7. **Debugging:**
   - Set breakpoints (click line number)
   - Press F5 or click Run and Debug (Ctrl+Shift+D)
   - Step through code with F10 (step over), F11 (step into)

**Useful VS Code Extensions for Spring Boot:**
- Spring Boot Extension Pack (includes Boot Dashboard)
- Gradle Extension
- REST Client (for testing endpoints)
- Docker (for container management)

---

## Troubleshooting

### Issue 1: Wrong Java Version Used for Build

**Symptom:**
```
[ERROR] COMPILATION ERROR: 
[ERROR] Source option 1.8 is no longer supported. Use 6 or later.
```

Or Maven reports wrong Java version:
```
[INFO] Building petclinic 4.0.0-SNAPSHOT
[INFO] ---
[INFO] Java version: 1.8.0_xxx, vendor: Oracle Corporation
```

**Solution:**

1. **Verify JAVA_HOME is set correctly:**
   ```bash
   echo $JAVA_HOME  # Unix/Linux/macOS
   echo %JAVA_HOME% # Windows
   
   # Should output path to Java 17 or 21 directory
   ```

2. **Switch Java versions:**
   ```bash
   # Unix/Linux/macOS
   export JAVA_HOME=/path/to/jdk17-or-21/
   
   # Or use the helper function from Prerequisites
   setjava17  # or setjava21
   
   # Windows (Command Prompt)
   set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.1+12
   ```

3. **Verify Java after switching:**
   ```bash
   java -version
   # Should show Java 17 or 21
   ```

4. **Clean and rebuild:**
   ```bash
   ./mvnw clean package
   ```

5. **Troubleshoot Maven/Gradle wrapper:**
   ```bash
   # Check which Java mvnw uses
   ./mvnw --version
   
   # If still wrong, configure in .mvn/maven.config or .mvn/jvm.config
   # Edit .mvn/jvm.config and add:
   # -Dfile.encoding=UTF-8 -Djava.version=17
   ```

**Prevention:**
- Always verify Java version before building
- Use `java -version` immediately after switching Java
- In CI/CD, explicitly specify Java path in build scripts

---

### Issue 2: Maven/Gradle Dependency Download Failures

**Symptom:**
```
[ERROR] Failed to execute goal on project petclinic: 
Could not resolve dependencies for project 
[FATAL ERROR] java.net.ConnectException: Connection refused: no further information
```

**Solution:**

1. **Check internet connectivity:**
   ```bash
   # Try downloading a file from Maven Central
   curl -I https://repo.maven.apache.org/maven2/
   # Should return HTTP 200
   ```

2. **Configure Maven proxy (if behind corporate proxy):**
   
   Edit `~/.m2/settings.xml` (create if doesn't exist):
   ```xml
   <settings>
     <proxies>
       <proxy>
         <id>httpProxy</id>
         <active>true</active>
         <protocol>http</protocol>
         <host>proxy.company.com</host>
         <port>8080</port>
         <username>user</username>
         <password>pass</password>
         <nonProxyHosts>localhost,127.0.0.1,*.internal.com</nonProxyHosts>
       </proxy>
     </proxies>
   </settings>
   ```

3. **Clear local Maven cache:**
   ```bash
   rm -rf ~/.m2/repository
   # Or on Windows: del /S %USERPROFILE%\.m2\repository
   
   # Rebuild - this will re-download all dependencies
   ./mvnw clean package
   ```

4. **Check Maven repository mirrors:**
   
   Edit `~/.m2/settings.xml`:
   ```xml
   <mirrors>
     <mirror>
       <id>aliyun</id>
       <mirrorOf>central</mirrorOf>
       <name>Aliyun Central Repository</name>
       <url>https://maven.aliyun.com/repository/public</url>
     </mirror>
   </mirrors>
   ```

5. **For Gradle, configure repositories:**
   
   Edit `build.gradle`:
   ```gradle
   repositories {
     maven { url "https://maven.aliyun.com/repository/public" }
     mavenCentral()
   }
   ```

6. **Try offline mode (if dependencies are cached):**
   ```bash
   ./mvnw clean package -o
   ./gradlew build --offline
   ```

---

### Issue 3: Database Connection Failures

**Symptom (H2):**
```
java.sql.SQLNonTransientConnectionException: 
Connection refused: no further information
```

**Symptom (MySQL/PostgreSQL):**
```
com.mysql.cj.jdbc.exceptions.CommunicationsException: 
Communications link failure: The last packet sent successfully 
to the server was 0 milliseconds ago.
```

**Solution for H2 (should be automatic):**
```bash
# H2 is embedded, no external database needed
# If getting connection errors, ensure no other instance is running

# Check if port 8080 (web server) is available
lsof -i :8080  # Unix/Linux/macOS
netstat -ano | findstr :8080  # Windows
```

**Solution for MySQL:**

1. **Verify Docker container is running:**
   ```bash
   docker ps | grep mysql
   # Should show running mysql:9.5 container
   
   # If not running:
   docker compose up mysql
   ```

2. **Test MySQL connection:**
   ```bash
   # If mysql-client installed
   mysql -h 127.0.0.1 -u petclinic -p petclinic -e "SELECT 1;"
   # Enter password: petclinic
   
   # Or via Docker
   docker exec $(docker ps -q -f "image=mysql:9.5") \
     mysql -u petclinic -ppetclinic -e "SELECT 1;"
   ```

3. **Check application.properties MySQL profile:**
   ```properties
   # In application-mysql.properties or env vars:
   spring.datasource.url=jdbc:mysql://localhost:3306/petclinic
   spring.datasource.username=petclinic
   spring.datasource.password=petclinic
   ```

4. **Verify MySQL container port mapping:**
   ```bash
   docker ps -a
   # Should show: 0.0.0.0:3306->3306/tcp
   ```

5. **Check if database exists:**
   ```bash
   docker exec $(docker ps -q -f "image=mysql:9.5") \
     mysql -u root -proot -e "SHOW DATABASES;"
   # petclinic database should be listed
   ```

**Solution for PostgreSQL:**

1. **Verify Docker container is running:**
   ```bash
   docker ps | grep postgres
   # Should show running postgres:18.1 container
   
   # If not running:
   docker compose up postgres
   ```

2. **Test PostgreSQL connection:**
   ```bash
   # If psql installed
   psql -h 127.0.0.1 -U petclinic -d petclinic -c "SELECT 1;"
   # Enter password: petclinic
   
   # Or via Docker
   docker exec $(docker ps -q -f "image=postgres:18.1") \
     psql -U petclinic -d petclinic -c "SELECT 1;"
   ```

3. **Check application.properties PostgreSQL profile:**
   ```properties
   # In application-postgres.properties or env vars:
   spring.datasource.url=jdbc:postgresql://localhost:5432/petclinic
   spring.datasource.username=petclinic
   spring.datasource.password=petclinic
   ```

4. **Verify PostgreSQL container port mapping:**
   ```bash
   docker ps -a
   # Should show: 0.0.0.0:5432->5432/tcp
   ```

5. **Check if database exists:**
   ```bash
   docker exec $(docker ps -q -f "image=postgres:18.1") \
     psql -U petclinic -l
   # petclinic database should be listed
   ```

**Environmental Variable Overrides:**
```bash
# For MySQL
export MYSQL_URL="jdbc:mysql://localhost:3306/petclinic"
export MYSQL_USER="petclinic"
export MYSQL_PASS="petclinic"

# For PostgreSQL
export POSTGRES_URL="jdbc:postgresql://localhost:5432/petclinic"
export POSTGRES_USER="petclinic"
export POSTGRES_PASS="petclinic"

# Then run:
java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=mysql
```

---

### Issue 4: Build Profile or Class Path Issues

**Symptom:**
```
[ERROR] The project you're trying to build contains compilation errors.
[ERROR] [error] object http is not a member of package javax
[ERROR] [error] object persistence is not a member of package jakarta
```

Or:
```
Exception in thread "main" java.lang.ClassNotFoundException: 
org.springframework.samples.petclinic.PetClinicApplication
```

**Solution:**

1. **Verify Java version compatibility:**
   ```bash
   java -version
   # Must be 17 or 21, not older versions
   ```

2. **Clean and rebuild completely:**
   ```bash
   ./mvnw clean
   rm -rf ~/.m2/repository/org/springframework/samples
   ./mvnw compile
   ```

3. **Check for corrupted dependencies:**
   ```bash
   # Remove local Maven cache
   rm -rf ~/.m2/repository
   
   # Rebuild
   ./mvnw clean install
   ```

4. **Verify pom.xml is valid:**
   ```bash
   # Validate POM structure
   ./mvnw validate
   
   # Run dependency check
   ./mvnw dependency:check
   ```

5. **For Gradle, clear build cache:**
   ```bash
   ./gradlew clean
   rm -rf ~/.gradle/caches
   ./gradlew build
   ```

6. **IDE-specific: Refresh classpath:**
   
   **IntelliJ:** File → Invalidate Caches → Invalidate and Restart
   
   **Eclipse:** Project → Clean → Select project → Build
   
   **VS Code:** Ctrl+Shift+A → "Java: Clean the language server workspace"

---

### Issue 5: Virtual Threads Configuration Not Recognized

**Symptom (Java 21 Variant B):**
```
ERROR o.s.b.d.LoggingFailureAnalysisReporter : 

***************************
APPLICATION FAILED TO START
***************************

Description:
Unknown property 'server.tomcat.virtual-threads.enabled'
```

Or when checking thread count, you see traditional threads instead of virtual threads.

**Solution:**

1. **Verify Spring Boot version supports virtual threads:**
   ```bash
   # Check pom.xml or build.gradle
   # Spring Boot 4.0.0+ includes virtual thread support
   ```

2. **Ensure you're using Java 21:**
   ```bash
   java -version
   # Must be Java 21 or later
   # Virtual threads were introduced in Java 19 as preview,
   # became standard in Java 21
   ```

3. **Create application-vthreads.properties if missing:**
   
   Create `src/main/resources/application-vthreads.properties`:
   ```properties
   server.tomcat.threads.max=10000
   server.tomcat.virtual-threads.enabled=true
   ```

4. **Rebuild with virtual threads profile:**
   ```bash
   ./mvnw clean package
   
   # Run with profile
   java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
     --spring.profiles.active=vthreads
   ```

5. **Verify virtual threads are enabled:**
   ```bash
   # Check application logs for:
   # "Tomcat initialized with protocol handler [org.apache.catalina.connector.Connector]"
   
   # Call the actuator endpoint to see thread details:
   curl http://localhost:8080/actuator/threaddump | grep -i virtual
   ```

6. **Alternative: Use environment variable:**
   ```bash
   export SPRING_PROFILES_ACTIVE=vthreads
   java -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
   ```

7. **Troubleshoot with debug logging:**
   ```properties
   # Add to application-vthreads.properties:
   logging.level.org.springframework.boot=DEBUG
   logging.level.org.apache.catalina=DEBUG
   ```

---

### Summary Table: Common Issues and Quick Fixes

| Issue | Command to Check | Quick Fix |
|-------|------------------|-----------|
| Wrong Java version | `java -version` | `export JAVA_HOME=/path/to/jdk21` |
| Maven dependency failure | `./mvnw validate` | `rm -rf ~/.m2/repository && ./mvnw clean install` |
| Database connection error (MySQL) | `docker ps` | `docker compose up mysql` |
| Database connection error (PostgreSQL) | `docker ps` | `docker compose up postgres` |
| Class not found exception | Rebuild | `./mvnw clean package` |
| Virtual threads not working | `curl localhost:8080/actuator/threaddump` | Set profile: `--spring.profiles.active=vthreads` |
| Port 8080 already in use | `lsof -i :8080` | Kill process or use: `java -jar ... --server.port=8081` |
| Out of memory | Application crashes | Increase: `java -Xmx4g -jar ...` |

---

## Quick Reference Commands

### One-Command Build and Run

**Java 17 Baseline with H2:**
```bash
./mvnw clean package -DskipTests && \
java -Xms512m -Xmx1g -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar
```

**Java 21 Traditional with MySQL:**
```bash
export JAVA_HOME=$JAVA_21_HOME && \
docker compose up mysql -d && \
./mvnw clean package -DskipTests && \
java -Xms1g -Xmx2g -XX:+UseG1GC -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=mysql
```

**Java 21 Virtual Threads with PostgreSQL:**
```bash
export JAVA_HOME=$JAVA_21_HOME && \
docker compose up postgres -d && \
./mvnw clean package -DskipTests && \
java -Xms512m -Xmx2g -XX:+UseG1GC \
  -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar \
  --spring.profiles.active=postgres,vthreads
```

### Database Management

```bash
# Start MySQL container
docker compose up mysql -d

# Start PostgreSQL container
docker compose up postgres -d

# Stop all containers
docker compose down

# View container logs
docker compose logs mysql
docker compose logs postgres

# Connect to MySQL directly
docker exec -it $(docker ps -q -f "image=mysql:9.5") \
  mysql -u petclinic -ppetclinic petclinic

# Connect to PostgreSQL directly
docker exec -it $(docker ps -q -f "image=postgres:18.1") \
  psql -U petclinic petclinic
```

### Performance Analysis

```bash
# With JFR recording
java -XX:+UnlockCommercialFeatures \
  -XX:+FlightRecorder \
  -XX:StartFlightRecording=filename=recording.jfr \
  -jar target/spring-petclinic-4.0.0-SNAPSHOT.jar

# Check JVM threads (compare variants)
curl http://localhost:8080/actuator/threaddump > threaddump.txt
grep -c "tid=" threaddump.txt  # Count threads

# Monitor heap usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

---

## Next Steps

After completing setup:

1. **Verify All Three Builds Succeed:**
   - [ ] Java 17 baseline builds and runs with H2
   - [ ] Java 21 Variant A builds and runs with database backend
   - [ ] Java 21 Variant B builds and runs with virtual threads

2. **Test Database Connectivity:**
   - [ ] H2 in-memory works without configuration
   - [ ] MySQL container starts and application connects
   - [ ] PostgreSQL container starts and application connects

3. **Prepare for Benchmarking:**
   - [ ] JFR recording configured for performance analysis
   - [ ] Actuator endpoints verified for metrics collection
   - [ ] JVM arguments optimized for your benchmark scenario

4. **Run Benchmarks (Next Phase):**
   - Proceed to benchmark execution workflows documentation
   - Configure load testing with JMeter or Gatling
   - Compare performance across the three variants

---

**Document Version:** 1.0
**Last Updated:** 2024
**Java Versions Supported:** 17, 21
**Spring Boot Version:** 4.0.1+

