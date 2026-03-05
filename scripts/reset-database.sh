#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

DB_TYPE=${1:-h2}

echo "=== Database Reset ==="
echo "Database Type: $DB_TYPE"

case $DB_TYPE in
    h2)
        echo "✓ H2 (in-memory) - no reset needed"
        echo "  (Database is recreated on application restart)"
        ;;
    mysql)
        echo "Resetting MySQL..."
        
        # Check if docker-compose is available
        if ! command -v docker-compose &> /dev/null && ! command -v docker &> /dev/null; then
            echo "Error: Docker/Docker Compose not available"
            exit 1
        fi
        
        # Start MySQL if not running
        cd "$PROJECT_ROOT"
        docker-compose up -d mysql
        sleep 5
        
        # Reset database
        docker-compose exec -T mysql mysql -u petclinic -ppetclinic petclinic \
            -e "DROP SCHEMA IF EXISTS petclinic; CREATE SCHEMA petclinic;" 2>/dev/null || true
        
        # Reload schema (check if schema file exists)
        SCHEMA_FILE="$PROJECT_ROOT/src/main/resources/db/mysql/schema.sql"
        DATA_FILE="$PROJECT_ROOT/src/main/resources/db/mysql/data.sql"
        
        if [ -f "$SCHEMA_FILE" ]; then
            docker-compose exec -T mysql mysql -u petclinic -ppetclinic petclinic \
                < "$SCHEMA_FILE" 2>/dev/null || true
        fi
        
        if [ -f "$DATA_FILE" ]; then
            docker-compose exec -T mysql mysql -u petclinic -ppetclinic petclinic \
                < "$DATA_FILE" 2>/dev/null || true
        fi
        
        echo "✓ MySQL reset completed"
        ;;
    postgres)
        echo "Resetting PostgreSQL..."
        
        # Check if docker-compose is available
        if ! command -v docker-compose &> /dev/null && ! command -v docker &> /dev/null; then
            echo "Error: Docker/Docker Compose not available"
            exit 1
        fi
        
        # Start PostgreSQL if not running
        cd "$PROJECT_ROOT"
        docker-compose up -d postgres
        sleep 5
        
        # Reset database
        docker-compose exec -T postgres psql -U petclinic -d postgres \
            -c "DROP DATABASE IF EXISTS petclinic; CREATE DATABASE petclinic;" 2>/dev/null || true
        
        # Reload schema
        SCHEMA_FILE="$PROJECT_ROOT/src/main/resources/db/postgresql/schema.sql"
        DATA_FILE="$PROJECT_ROOT/src/main/resources/db/postgresql/data.sql"
        
        if [ -f "$SCHEMA_FILE" ]; then
            docker-compose exec -T postgres psql -U petclinic -d petclinic \
                < "$SCHEMA_FILE" 2>/dev/null || true
        fi
        
        if [ -f "$DATA_FILE" ]; then
            docker-compose exec -T postgres psql -U petclinic -d petclinic \
                < "$DATA_FILE" 2>/dev/null || true
        fi
        
        echo "✓ PostgreSQL reset completed"
        ;;
    *)
        echo "Error: Unknown database type: $DB_TYPE"
        echo "Usage: $0 {h2|mysql|postgres}"
        exit 1
        ;;
esac

echo ""
echo "Ready for new benchmark run"
