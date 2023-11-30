/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPENSEARCH_PROFILE;

@Getter
@AllArgsConstructor
public enum DatabaseProfile {

  ELASTICSEARCH(ELASTICSEARCH_PROFILE),
  OPENSEARCH(OPENSEARCH_PROFILE);

  private final String id;

  @JsonValue
  public String getId() {
    return this.name().toLowerCase();
  }

  public static DatabaseProfile toProfile(final String profileString) {
    return valueOf(profileString.toUpperCase());
  }

}
