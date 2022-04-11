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
public class DashboardStringVariableFilterDataDto extends DashboardVariableFilterDataDto {
  protected List<String> defaultValues;

  protected DashboardStringVariableFilterDataDto() {
    this(null, new DashboardVariableFilterSubDataDto(null, null, false));
  }

  public DashboardStringVariableFilterDataDto(final String name, final DashboardVariableFilterSubDataDto data) {
    this(name, data, null);
  }

  public DashboardStringVariableFilterDataDto(final String name, final DashboardVariableFilterSubDataDto data,
                                              final List<String> defaultValues) {
    super(VariableType.STRING, name, data);
    this.defaultValues = defaultValues;
  }
}
