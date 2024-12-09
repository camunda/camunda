/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.config;

public enum ConnectionTypes {
  ELASTICSEARCH("elasticsearch"),
  OPENSEARCH("opensearch");

  private final String type;

  ConnectionTypes(final String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public static ConnectionTypes from(final String type) {
    return valueOf(type.toUpperCase());
  }

  public static boolean isElasticSearch(final String type) {
    return from(type) == ELASTICSEARCH;
  }
}
