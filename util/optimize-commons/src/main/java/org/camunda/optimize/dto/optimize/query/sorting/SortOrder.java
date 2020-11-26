/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.sorting;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public enum SortOrder {
  @JsonProperty("asc")
  ASC,
  @JsonProperty("desc")
  DESC,
  ;

  // This is used for parameter deserialization under the hood as part of Jersey's TypeFromStringEnum
  @SuppressWarnings(UNUSED)
  public static SortOrder fromString(String sortOrderParam) {
    return valueOf(sortOrderParam.toUpperCase());
  }

}
