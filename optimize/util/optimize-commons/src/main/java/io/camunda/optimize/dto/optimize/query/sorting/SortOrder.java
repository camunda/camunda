/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sorting;

import static io.camunda.optimize.util.SuppressionConstants.UNUSED;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SortOrder {
  @JsonProperty("asc")
  ASC,
  @JsonProperty("desc")
  DESC;

  // This is used for parameter deserialization under the hood as part of Jersey's
  // TypeFromStringEnum
  @SuppressWarnings(UNUSED)
  public static SortOrder fromString(final String sortOrderParam) {
    return valueOf(sortOrderParam.toUpperCase());
  }
}
