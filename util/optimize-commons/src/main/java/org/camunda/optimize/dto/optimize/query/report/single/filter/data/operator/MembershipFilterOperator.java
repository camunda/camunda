/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;

import java.util.Set;

@AllArgsConstructor
public enum MembershipFilterOperator {
  IN(FilterOperatorConstants.IN),
  NOT_IN(FilterOperatorConstants.NOT_IN);

  private final String id;

  @JsonValue
  public String getId() {
    return id;
  }

}
