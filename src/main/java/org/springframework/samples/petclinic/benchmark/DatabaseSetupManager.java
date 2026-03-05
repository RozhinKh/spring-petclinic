/*
 * Copyright 2015-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Manages database setup, initialization, and reset for load testing.
 * Supports H2, MySQL, and PostgreSQL databases.
 */
public class DatabaseSetupManager {

	private final String databaseType;
	private final String dbDriver;
	private final String dbUrl;
	private final String dbUsername;
	private final String dbPassword;
	private final String resetScriptPath;

	public DatabaseSetupManager(String databaseType, String dbDriver, String dbUrl, String dbUsername,
			String dbPassword, String resetScriptPath) {
		this.databaseType = databaseType;
		this.dbDriver = dbDriver;
		this.dbUrl = dbUrl;
		this.dbUsername = dbUsername;
		this.dbPassword = dbPassword;
		this.resetScriptPath = resetScriptPath;
	}

	/**
	 * Initialize database with schema and default data.
	 */
	public void initializeDatabase() throws Exception {
		ensureDriverLoaded();
		try (Connection connection = getConnection()) {
			executeSqlScript(connection, getInitScriptPath());
			System.out.println("✓ Database initialized: " + databaseType);
		}
	}

	/**
	 * Reset database by dropping all tables and re-initializing.
	 */
	public void resetDatabase() throws Exception {
		ensureDriverLoaded();
		try (Connection connection = getConnection()) {
			String script = getResetScriptPath();
			if (script != null && !script.isEmpty()) {
				executeSqlScript(connection, script);
				System.out.println("✓ Database reset: " + databaseType);
			}
		}
	}

	/**
	 * Verify database connectivity and accessibility.
	 */
	public void verifyConnectivity() throws Exception {
		ensureDriverLoaded();
		try (Connection connection = getConnection()) {
			if (connection.isValid(5)) {
				System.out.println("✓ Database connectivity verified: " + databaseType);
			} else {
				throw new SQLException("Database connection validation failed");
			}
		}
	}

	/**
	 * Get record count from table (for data validation).
	 */
	public long getRecordCount(String tableName) throws Exception {
		ensureDriverLoaded();
		try (Connection connection = getConnection();
				Statement stmt = connection.createStatement();
				var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
			if (rs.next()) {
				return rs.getLong(1);
			}
			return 0;
		}
	}

	/**
	 * Execute SQL script from file.
	 */
	private void executeSqlScript(Connection connection, String scriptPath) throws IOException, SQLException {
		String sqlScript = readSqlScript(scriptPath);

		// Split by semicolon and execute statements
		String[] statements = sqlScript.split(";");

		try (Statement stmt = connection.createStatement()) {
			for (String statement : statements) {
				String trimmed = statement.trim();
				if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
					stmt.execute(trimmed);
				}
			}
			connection.commit();
		}
	}

	/**
	 * Read SQL script from file, removing comments and extra whitespace.
	 */
	private String readSqlScript(String scriptPath) throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(scriptPath), StandardCharsets.UTF_8);

		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			String trimmed = line.trim();
			// Skip comments and empty lines
			if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
				sb.append(trimmed).append("\n");
			}
		}

		return sb.toString();
	}

	/**
	 * Get path to initialization script based on database type.
	 */
	private String getInitScriptPath() {
		return switch (databaseType.toLowerCase()) {
			case "h2" -> "src/main/resources/db/h2/schema.sql";
			case "mysql" -> "src/main/resources/db/mysql/schema.sql";
			case "postgres" -> "src/main/resources/db/postgres/schema.sql";
			default -> throw new IllegalArgumentException("Unsupported database: " + databaseType);
		};
	}

	/**
	 * Get path to reset script.
	 */
	private String getResetScriptPath() {
		if (resetScriptPath != null && !resetScriptPath.isEmpty()) {
			return resetScriptPath;
		}

		return switch (databaseType.toLowerCase()) {
			case "h2" -> "src/main/resources/db/h2/reset.sql";
			case "mysql" -> "src/main/resources/db/mysql/reset.sql";
			case "postgres" -> "src/main/resources/db/postgres/reset.sql";
			default -> throw new IllegalArgumentException("Unsupported database: " + databaseType);
		};
	}

	/**
	 * Create database connection.
	 */
	private Connection getConnection() throws SQLException {
		if (dbPassword != null && !dbPassword.isEmpty()) {
			return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
		}
		return DriverManager.getConnection(dbUrl);
	}

	/**
	 * Ensure JDBC driver is loaded.
	 */
	private void ensureDriverLoaded() throws ClassNotFoundException {
		Class.forName(dbDriver);
	}

	/**
	 * Database type enum.
	 */
	public enum DatabaseType {
		H2("h2", "org.h2.Driver"), MYSQL("mysql", "com.mysql.cj.jdbc.Driver"),
		POSTGRES("postgres", "org.postgresql.Driver");

		private final String name;
		private final String driver;

		DatabaseType(String name, String driver) {
			this.name = name;
			this.driver = driver;
		}

		public String getName() {
			return name;
		}

		public String getDriver() {
			return driver;
		}

		public static DatabaseType fromName(String name) {
			for (DatabaseType type : values()) {
				if (type.name.equalsIgnoreCase(name)) {
					return type;
				}
			}
			throw new IllegalArgumentException("Unknown database type: " + name);
		}
	}

}
