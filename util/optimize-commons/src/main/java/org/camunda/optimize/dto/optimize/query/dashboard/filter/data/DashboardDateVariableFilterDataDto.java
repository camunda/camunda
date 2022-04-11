/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

@EqualsAndHashCode(callSuper = true)
@Data
public class DashboardDateVariableFilterDataDto extends DashboardVariableFilterDataDto {
  protected DateFilterDataDto<?> defaultValues;

  protected DashboardDateVariableFilterDataDto() {
    this(null);
  }

  public DashboardDateVariableFilterDataDto(final String name) {
    this(name, null, null);
  }

  public DashboardDateVariableFilterDataDto(final String name, final DashboardVariableFilterSubDataDto data,
                                            final DateFilterDataDto<?> defaultValues) {
    super(VariableType.DATE, name, data);
    this.defaultValues = defaultValues;
  }
}
