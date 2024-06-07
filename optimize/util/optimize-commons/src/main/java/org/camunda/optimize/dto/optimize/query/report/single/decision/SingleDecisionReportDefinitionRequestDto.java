/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

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
