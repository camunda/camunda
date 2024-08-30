/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DashboardStringVariableFilterDataDto extends DashboardVariableFilterDataDto {
  protected List<String> defaultValues;

  protected DashboardStringVariableFilterDataDto() {
    this(null, new DashboardVariableFilterSubDataDto(null, null, false));
  }

  public DashboardStringVariableFilterDataDto(
      final String name, final DashboardVariableFilterSubDataDto data) {
    this(name, data, null);
  }

  public DashboardStringVariableFilterDataDto(
      final String name,
      final DashboardVariableFilterSubDataDto data,
      final List<String> defaultValues) {
    super(VariableType.STRING, name, data);
    this.defaultValues = defaultValues;
  }
}
