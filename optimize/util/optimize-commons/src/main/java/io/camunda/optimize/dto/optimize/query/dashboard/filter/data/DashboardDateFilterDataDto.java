/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import lombok.Data;

@Data
public class DashboardDateFilterDataDto implements FilterDataDto {

  private DateFilterDataDto<?> defaultValues;

  public DashboardDateFilterDataDto(DateFilterDataDto<?> defaultValues) {
    this.defaultValues = defaultValues;
  }

  protected DashboardDateFilterDataDto() {}

  public static final class Fields {

    public static final String defaultValues = "defaultValues";
  }
}
