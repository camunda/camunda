/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sorting;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SortOrder {
  @JsonProperty("asc")
  ASC,
  @JsonProperty("desc")
  DESC;
}
