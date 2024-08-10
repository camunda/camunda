/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.util.configuration.users.AuthorizedUserType;
import lombok.Data;

@Data
public class CsvConfiguration {

  @JsonProperty("limit")
  private Integer exportCsvLimit;

  @JsonProperty("delimiter")
  private Character exportCsvDelimiter;

  @JsonProperty("authorizedUsers")
  private AuthorizedUserType authorizedUserType;
}
