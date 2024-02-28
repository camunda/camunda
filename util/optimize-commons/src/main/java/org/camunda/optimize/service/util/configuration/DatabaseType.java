/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_DATABASE_PROPERTY;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPENSEARCH_DATABASE_PROPERTY;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DatabaseType {
  ELASTICSEARCH(ELASTICSEARCH_DATABASE_PROPERTY),
  OPENSEARCH(OPENSEARCH_DATABASE_PROPERTY);

  private final String id;

  @JsonValue
  public String getId() {
    return this.name().toLowerCase();
  }

  public static DatabaseType fromString(final String profileString) {
    return valueOf(profileString.toUpperCase());
  }

  @Override
  public String toString() {
    return getId();
  }
}
