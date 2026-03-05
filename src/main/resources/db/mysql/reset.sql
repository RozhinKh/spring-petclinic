-- MySQL Database Reset Script
-- Drops all PetClinic tables and re-initializes schema and test data
-- Safe to execute multiple times

-- Set SQL mode for compatibility
SET SQL_MODE='STRICT_TRANS_TABLES';

-- Drop all tables (order matters due to foreign keys)
DROP TABLE IF EXISTS visits;
DROP TABLE IF EXISTS vet_specialties;
DROP TABLE IF EXISTS pets;
DROP TABLE IF EXISTS owners;
DROP TABLE IF EXISTS types;
DROP TABLE IF EXISTS specialties;
DROP TABLE IF EXISTS vets;

-- Create tables with explicit character set
CREATE TABLE types (
  id INT(4) PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE owners (
  id INT(4) PRIMARY KEY AUTO_INCREMENT,
  first_name VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  last_name VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  address VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  city VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  telephone VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE pets (
  id INT(4) PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  birth_date DATE,
  type_id INT(4) NOT NULL,
  owner_id INT(4) NOT NULL,
  FOREIGN KEY (type_id) REFERENCES types(id),
  FOREIGN KEY (owner_id) REFERENCES owners(id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE vets (
  id INT(4) PRIMARY KEY AUTO_INCREMENT,
  first_name VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  last_name VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE specialties (
  id INT(4) PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE vet_specialties (
  vet_id INT(4) NOT NULL,
  specialty_id INT(4) NOT NULL,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (specialty_id) REFERENCES specialties(id),
  PRIMARY KEY (vet_id, specialty_id)
);

CREATE TABLE visits (
  id INT(4) PRIMARY KEY AUTO_INCREMENT,
  pet_id INT(4) NOT NULL,
  visit_date DATE,
  description VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  FOREIGN KEY (pet_id) REFERENCES pets(id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Insert default data
INSERT INTO types VALUES (1, 'cat');
INSERT INTO types VALUES (2, 'dog');
INSERT INTO types VALUES (3, 'lizard');
INSERT INTO types VALUES (4, 'snake');
INSERT INTO types VALUES (5, 'bird');
INSERT INTO types VALUES (6, 'hamster');

INSERT INTO vets VALUES (1, 'James', 'Carter');
INSERT INTO vets VALUES (2, 'Helen', 'Leary');
INSERT INTO vets VALUES (3, 'Linda', 'Douglas');
INSERT INTO vets VALUES (4, 'Rafael', 'Ortega');
INSERT INTO vets VALUES (5, 'Henry', 'Stevens');

INSERT INTO specialties VALUES (1, 'radiology');
INSERT INTO specialties VALUES (2, 'surgery');
INSERT INTO specialties VALUES (3, 'dentistry');

INSERT INTO vet_specialties VALUES (2, 1);
INSERT INTO vet_specialties VALUES (3, 2);
INSERT INTO vet_specialties VALUES (3, 3);
INSERT INTO vet_specialties VALUES (4, 2);
INSERT INTO vet_specialties VALUES (5, 1);
