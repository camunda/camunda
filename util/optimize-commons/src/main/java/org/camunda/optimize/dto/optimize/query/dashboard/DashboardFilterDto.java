/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.DashboardFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DashboardVariableFilterDataDto;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class DashboardFilterDto {
  private DashboardFilterType type;
  private DashboardVariableFilterDataDto data;
}
