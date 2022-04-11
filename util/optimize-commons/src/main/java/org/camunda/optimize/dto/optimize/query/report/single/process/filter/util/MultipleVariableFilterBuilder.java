/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.MultipleVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.MultipleVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;

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
    this.variableFilters = variableFilters.stream().map(ProcessFilterDto::getData).collect(Collectors.toList());
    return this;
  }

  public ProcessFilterBuilder add() {
    MultipleVariableFilterDataDto variableFilterDataDto = new MultipleVariableFilterDataDto(variableFilters);
    MultipleVariableFilterDto variableFilterDto = new MultipleVariableFilterDto();
    variableFilterDto.setData(variableFilterDataDto);
    variableFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(variableFilterDto);
    return filterBuilder;
  }

}
