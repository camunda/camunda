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

public final class MultipleVariableFilterBuilder {

  private static final FilterApplicationLevel FILTER_LEVEL = FilterApplicationLevel.INSTANCE;

  private final ProcessFilterBuilder filterBuilder;
  private List<VariableFilterDataDto<?>> variableFilters = new ArrayList<>();

  private MultipleVariableFilterBuilder(final ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static MultipleVariableFilterBuilder construct(final ProcessFilterBuilder filterBuilder) {
    return new MultipleVariableFilterBuilder(filterBuilder);
  }

  public MultipleVariableFilterBuilder type(final List<VariableFilterDataDto<?>> variableFilters) {
    this.variableFilters = variableFilters;
    return this;
  }

  public MultipleVariableFilterBuilder variableFilters(
      final List<VariableFilterDto> variableFilters) {
    this.variableFilters =
        variableFilters.stream().map(ProcessFilterDto::getData).collect(Collectors.toList());
    return this;
  }

  public ProcessFilterBuilder add() {
    final MultipleVariableFilterDataDto variableFilterDataDto =
        new MultipleVariableFilterDataDto(variableFilters);
    final MultipleVariableFilterDto variableFilterDto = new MultipleVariableFilterDto();
    variableFilterDto.setData(variableFilterDataDto);
    variableFilterDto.setFilterLevel(FILTER_LEVEL);
    filterBuilder.addFilter(variableFilterDto);
    return filterBuilder;
  }
}
