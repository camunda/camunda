/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_DATABASE_PROPERTY;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPENSEARCH_DATABASE_PROPERTY;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum DatabaseType {
  ELASTICSEARCH(ELASTICSEARCH_DATABASE_PROPERTY),
  OPENSEARCH(OPENSEARCH_DATABASE_PROPERTY);

  private final String id;

  private DatabaseType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return name().toLowerCase(Locale.ENGLISH);
  }

  public static DatabaseType fromString(final String profileString) {
    return valueOf(profileString.toUpperCase());
  }

  @Override
  public String toString() {
    return getId();
  }
}
