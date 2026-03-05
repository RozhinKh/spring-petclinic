-- PostgreSQL Database Reset Script
-- Drops all PetClinic tables and re-initializes schema and test data
-- Safe to execute multiple times

-- Drop all tables (order matters due to foreign keys)
DROP TABLE IF EXISTS visits CASCADE;
DROP TABLE IF EXISTS vet_specialties CASCADE;
DROP TABLE IF EXISTS pets CASCADE;
DROP TABLE IF EXISTS owners CASCADE;
DROP TABLE IF EXISTS types CASCADE;
DROP TABLE IF EXISTS specialties CASCADE;
DROP TABLE IF EXISTS vets CASCADE;

-- Create sequences
CREATE SEQUENCE types_id_seq START 1;
CREATE SEQUENCE owners_id_seq START 1;
CREATE SEQUENCE pets_id_seq START 1;
CREATE SEQUENCE vets_id_seq START 1;
CREATE SEQUENCE specialties_id_seq START 1;
CREATE SEQUENCE visits_id_seq START 1;

-- Create tables
CREATE TABLE types (
  id INT DEFAULT nextval('types_id_seq') PRIMARY KEY,
  name VARCHAR(80)
);

CREATE TABLE owners (
  id INT DEFAULT nextval('owners_id_seq') PRIMARY KEY,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  address VARCHAR(255),
  city VARCHAR(80),
  telephone VARCHAR(20)
);

CREATE TABLE pets (
  id INT DEFAULT nextval('pets_id_seq') PRIMARY KEY,
  name VARCHAR(30),
  birth_date DATE,
  type_id INT NOT NULL,
  owner_id INT NOT NULL,
  FOREIGN KEY (type_id) REFERENCES types(id),
  FOREIGN KEY (owner_id) REFERENCES owners(id)
);

CREATE TABLE vets (
  id INT DEFAULT nextval('vets_id_seq') PRIMARY KEY,
  first_name VARCHAR(30),
  last_name VARCHAR(30)
);

CREATE TABLE specialties (
  id INT DEFAULT nextval('specialties_id_seq') PRIMARY KEY,
  name VARCHAR(80)
);

CREATE TABLE vet_specialties (
  vet_id INT NOT NULL,
  specialty_id INT NOT NULL,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (specialty_id) REFERENCES specialties(id),
  PRIMARY KEY (vet_id, specialty_id)
);

CREATE TABLE visits (
  id INT DEFAULT nextval('visits_id_seq') PRIMARY KEY,
  pet_id INT NOT NULL,
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

-- Reset sequences to continue from max ID
SELECT setval('types_id_seq', COALESCE(MAX(id), 1)) FROM types;
SELECT setval('vets_id_seq', COALESCE(MAX(id), 1)) FROM vets;
SELECT setval('specialties_id_seq', COALESCE(MAX(id), 1)) FROM specialties;
