/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;

public class DateVariableFilterDataDto extends VariableFilterDataDto<DateFilterDataDto<?>> {
  protected DateVariableFilterDataDto() {
    this(null, null);
  }

  public DateVariableFilterDataDto(final String name, final DateFilterDataDto<?> data) {
    super(name, VariableType.DATE, data);
  }
}
