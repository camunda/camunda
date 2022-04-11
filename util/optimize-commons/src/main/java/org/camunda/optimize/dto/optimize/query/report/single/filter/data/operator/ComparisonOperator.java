/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;

@AllArgsConstructor
public enum ComparisonOperator {

  LESS_THAN(FilterOperatorConstants.LESS_THAN),
  LESS_THAN_EQUALS(FilterOperatorConstants.LESS_THAN_EQUALS),
  GREATER_THAN(FilterOperatorConstants.GREATER_THAN),
  GREATER_THAN_EQUALS(FilterOperatorConstants.GREATER_THAN_EQUALS),
  ;

  private final String id;

  @JsonValue
  public String getId() {
    return id;
  }

}
