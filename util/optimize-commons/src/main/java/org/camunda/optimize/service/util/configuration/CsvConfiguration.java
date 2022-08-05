/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;

@Data
public class CsvConfiguration {

  @JsonProperty("limit")
  private Integer exportCsvLimit;
  @JsonProperty("delimiter")
  private Character exportCsvDelimiter;
  @JsonProperty("authorizedUsers")
  private AuthorizedUserType authorizedUserType;

  public enum AuthorizedUserType {
    ALL, SUPERUSER, NONE;

    @JsonValue
    public String getId() {
      return this.name().toLowerCase();
    }

    @Override
    public String toString() {
      return getId();
    }
  }
}
