/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.sorting;

import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SortOrder {
  @JsonProperty("asc")
  ASC,
  @JsonProperty("desc")
  DESC,
  ;

  // This is used for parameter deserialization under the hood as part of Jersey's
  // TypeFromStringEnum
  @SuppressWarnings(UNUSED)
  public static SortOrder fromString(String sortOrderParam) {
    return valueOf(sortOrderParam.toUpperCase());
  }
}
