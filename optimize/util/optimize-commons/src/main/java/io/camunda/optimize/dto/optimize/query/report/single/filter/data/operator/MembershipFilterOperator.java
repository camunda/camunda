/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator;

import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;

public enum MembershipFilterOperator {
  IN(FilterOperatorConstants.IN),
  NOT_IN(FilterOperatorConstants.NOT_IN);

  private final String id;

  private MembershipFilterOperator(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}
