/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import java.util.List;

public class SingleDecisionReportDefinitionRequestDto
    extends SingleReportDefinitionDto<DecisionReportDataDto> {

  public SingleDecisionReportDefinitionRequestDto() {
    this(new DecisionReportDataDto());
  }

  public SingleDecisionReportDefinitionRequestDto(final DecisionReportDataDto data) {
    super(data, false, ReportType.DECISION);
  }

  @Override
  public ReportType getReportType() {
    return super.getReportType();
  }

  @JsonIgnore
  public List<FilterDataDto> getFilterData() {
    return data.getFilter().stream().map(DecisionFilterDto::getData).collect(toList());
  }

  public static SingleDecisionReportDefinitionRequestDtoBuilder<?, ?> builder() {
    return new SingleDecisionReportDefinitionRequestDtoBuilderImpl();
  }

  public abstract static class SingleDecisionReportDefinitionRequestDtoBuilder<
          C extends SingleDecisionReportDefinitionRequestDto,
          B extends SingleDecisionReportDefinitionRequestDtoBuilder<C, B>>
      extends SingleReportDefinitionDtoBuilder<DecisionReportDataDto, C, B> {

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "SingleDecisionReportDefinitionRequestDto.SingleDecisionReportDefinitionRequestDtoBuilder(super="
          + super.toString()
          + ")";
    }
  }

  private static final class SingleDecisionReportDefinitionRequestDtoBuilderImpl
      extends SingleDecisionReportDefinitionRequestDtoBuilder<
          SingleDecisionReportDefinitionRequestDto,
          SingleDecisionReportDefinitionRequestDtoBuilderImpl> {

    private SingleDecisionReportDefinitionRequestDtoBuilderImpl() {}

    @Override
    protected SingleDecisionReportDefinitionRequestDtoBuilderImpl self() {
      return this;
    }

    @Override
    public SingleDecisionReportDefinitionRequestDto build() {
      return new SingleDecisionReportDefinitionRequestDto(getData());
    }
  }
}
