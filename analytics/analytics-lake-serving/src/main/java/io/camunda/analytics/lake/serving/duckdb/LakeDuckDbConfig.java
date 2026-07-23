/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Owns this application's single embedded DuckDB connection. The connection backs an in-memory
 * DuckDB instance — there is no on-disk DuckDB database file for this app to open in DuckDB's own
 * {@code ACCESS_MODE=READ_ONLY} sense — and this module only ever issues {@code CREATE VIEW} and
 * {@code SELECT} statements against it (see {@link LakeViewRegistry} and {@link LakeQueryService}
 * respectively): it never writes to the lake warehouse itself, so "read-only" here describes how
 * this connection is used, not a DuckDB-level flag.
 */
@Configuration
public class LakeDuckDbConfig {

  @Bean(destroyMethod = "close")
  public Connection lakeDuckDbConnection() throws SQLException {
    return DriverManager.getConnection("jdbc:duckdb:");
  }
}
