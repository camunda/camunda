/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Data
public class DecisionReportDataDto extends SingleReportDataDto implements Combinable {

  protected String decisionDefinitionKey;
  protected String decisionDefinitionVersion;
  protected List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));
  protected List<DecisionFilterDto> filter = new ArrayList<>();
  protected DecisionViewDto view;
  protected DecisionGroupByDto groupBy;
  protected DecisionVisualization visualization;
  protected DecisionParametersDto parameters = new DecisionParametersDto();

  @JsonIgnore
  @Override
  public String getDefinitionKey() {
    return decisionDefinitionKey;
  }

  @JsonIgnore
  @Override
  public String getDefinitionVersion() {
    return decisionDefinitionVersion;
  }

  @JsonIgnore
  @Override
  public String createCommandKey() {
    String viewCommandKey = view == null ? "null" : view.createCommandKey();
    String groupByCommandKey = groupBy == null ? "null" : groupBy.createCommandKey();
    return viewCommandKey + "_" + groupByCommandKey;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionReportDataDto)) {
      return false;
    }
    DecisionReportDataDto that = (DecisionReportDataDto) o;
    return ReportUtil.isCombinable(view, that.view) &&
      ReportUtil.isCombinable(groupBy, that.groupBy) &&
      Objects.equals(visualization, that.visualization);
  }

}
