/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

public class DateVariableFilterDataDto extends VariableFilterDataDto<DateFilterDataDto<?>> {
  protected DateVariableFilterDataDto() {
    this(null, null);
  }

  public DateVariableFilterDataDto(final String name, final DateFilterDataDto<?> data) {
    super(name, VariableType.DATE, data);
  }
}
