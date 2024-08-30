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
import lombok.experimental.SuperBuilder;

@SuperBuilder
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
}
