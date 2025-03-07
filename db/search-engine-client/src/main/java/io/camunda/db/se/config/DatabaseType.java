/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.se.config;

public enum DatabaseType {
  ELASTICSEARCH(DatabaseConfig.ELASTICSEARCH),
  OPENSEARCH(DatabaseConfig.OPENSEARCH),
  RDBMS(DatabaseConfig.RDBMS);

  private final String type;

  DatabaseType(final String type) {
    this.type = type;
  }

  public static DatabaseType from(final String type) {
    return DatabaseType.valueOf(type.toUpperCase());
  }

  public boolean isElasticSearch() {
    return equals(ELASTICSEARCH);
  }

  public boolean isOpenSearch() {
    return equals(OPENSEARCH);
  }

  public boolean isRdbms() {
    return equals(RDBMS);
  }

  @Override
  public String toString() {
    return type;
  }
}
