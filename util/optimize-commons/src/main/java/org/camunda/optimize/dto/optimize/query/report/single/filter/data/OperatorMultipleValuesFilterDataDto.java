/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public class OperatorMultipleValuesFilterDataDto {
  protected FilterOperator operator;
  protected List<String> values;

  public OperatorMultipleValuesFilterDataDto(final FilterOperator operator,
                                             final List<String> values) {
    this.operator = operator;
    this.values = Optional.ofNullable(values).orElseGet(ArrayList::new);
  }
}
