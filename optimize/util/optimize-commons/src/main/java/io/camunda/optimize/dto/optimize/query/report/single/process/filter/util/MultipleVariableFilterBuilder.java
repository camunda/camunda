/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.MultipleVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.MultipleVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MultipleVariableFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private List<VariableFilterDataDto<?>> variableFilters = new ArrayList<>();
  private static final FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private MultipleVariableFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static MultipleVariableFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new MultipleVariableFilterBuilder(filterBuilder);
  }

  public MultipleVariableFilterBuilder type(List<VariableFilterDataDto<?>> variableFilters) {
    this.variableFilters = variableFilters;
    return this;
  }

  public MultipleVariableFilterBuilder variableFilters(List<VariableFilterDto> variableFilters) {
    this.variableFilters =
        variableFilters.stream().map(ProcessFilterDto::getData).collect(Collectors.toList());
    return this;
  }

  public ProcessFilterBuilder add() {
    MultipleVariableFilterDataDto variableFilterDataDto =
        new MultipleVariableFilterDataDto(variableFilters);
    MultipleVariableFilterDto variableFilterDto = new MultipleVariableFilterDto();
    variableFilterDto.setData(variableFilterDataDto);
    variableFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(variableFilterDto);
    return filterBuilder;
  }
}
