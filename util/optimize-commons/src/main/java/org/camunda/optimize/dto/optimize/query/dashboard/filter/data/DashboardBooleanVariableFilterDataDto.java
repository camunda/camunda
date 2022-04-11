/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DashboardBooleanVariableFilterDataDto extends DashboardVariableFilterDataDto {
  protected List<Boolean> defaultValues;

  protected DashboardBooleanVariableFilterDataDto() {
    this(null);
  }

  public DashboardBooleanVariableFilterDataDto(final String name) {
    this(name, null, null);
  }

  public DashboardBooleanVariableFilterDataDto(final String name, final DashboardVariableFilterSubDataDto data,
                                               final List<Boolean> defaultValues) {
    super(VariableType.BOOLEAN, name, data);
    this.defaultValues = defaultValues;
  }
}
