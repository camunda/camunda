/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.search.engine.config;

public final class DatabaseConfig {

  public static final String ELASTICSEARCH = "elasticsearch";
  public static final String RDBMS = "rdbms";
  public static final String OPENSEARCH = "opensearch";

  private DatabaseConfig() {
  }
}
