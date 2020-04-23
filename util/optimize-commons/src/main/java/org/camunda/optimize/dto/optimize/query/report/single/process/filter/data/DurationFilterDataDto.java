/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class DurationFilterDataDto implements FilterDataDto {

  protected Long value;
  protected DurationFilterUnit unit;
  protected String operator;

}
