/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class DurationFilterDataDto implements FilterDataDto {

  protected Long value;
  protected DurationUnit unit;
  protected ComparisonOperator operator;
  protected boolean includeNull;

}
