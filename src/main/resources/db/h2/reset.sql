-- H2 Database Reset Script
-- Drops all PetClinic tables and re-initializes schema and test data
-- Safe to execute multiple times

-- Drop all tables (order matters due to foreign keys)
DROP TABLE IF EXISTS visits;
DROP TABLE IF EXISTS pets;
DROP TABLE IF EXISTS owners;
DROP TABLE IF EXISTS types;
DROP TABLE IF EXISTS specialties;
DROP TABLE IF EXISTS vet_specialties;
DROP TABLE IF EXISTS vets;

-- Re-create schema
CREATE TABLE types (
  id INTEGER PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80)
);

CREATE TABLE owners (
  id INTEGER PRIMARY KEY AUTO_INCREMENT,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  address VARCHAR(255),
  city VARCHAR(80),
  telephone VARCHAR(20)
);

CREATE TABLE pets (
  id INTEGER PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(30),
  birth_date DATE,
  type_id INTEGER NOT NULL,
  owner_id INTEGER NOT NULL,
  FOREIGN KEY (type_id) REFERENCES types(id),
  FOREIGN KEY (owner_id) REFERENCES owners(id)
);

CREATE TABLE vets (
  id INTEGER PRIMARY KEY AUTO_INCREMENT,
  first_name VARCHAR(30),
  last_name VARCHAR(30)
);

CREATE TABLE specialties (
  id INTEGER PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80)
);

CREATE TABLE vet_specialties (
  vet_id INTEGER NOT NULL,
  specialty_id INTEGER NOT NULL,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (specialty_id) REFERENCES specialties(id),
  PRIMARY KEY (vet_id, specialty_id)
);

CREATE TABLE visits (
  id INTEGER PRIMARY KEY AUTO_INCREMENT,
  pet_id INTEGER NOT NULL,
  visit_date DATE,
  description VARCHAR(255),
  FOREIGN KEY (pet_id) REFERENCES pets(id)
);

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
