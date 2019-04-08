/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DecisionReportDataDto extends SingleReportDataDto implements Combinable {

  protected String decisionDefinitionKey;
  protected String decisionDefinitionVersion;
  protected List<DecisionFilterDto> filter = new ArrayList<>();
  protected DecisionViewDto view;
  protected DecisionGroupByDto groupBy;
  protected DecisionVisualization visualization;
  protected DecisionParametersDto parameters = new DecisionParametersDto();

  public String getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public void setDecisionDefinitionKey(final String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public String getDecisionDefinitionVersion() {
    return decisionDefinitionVersion;
  }

  public void setDecisionDefinitionVersion(final String decisionDefinitionVersion) {
    this.decisionDefinitionVersion = decisionDefinitionVersion;
  }

  public List<DecisionFilterDto> getFilter() {
    return filter;
  }

  public void setFilter(List<DecisionFilterDto> filter) {
    this.filter = filter;
  }

  public DecisionViewDto getView() {
    return view;
  }

  public void setView(DecisionViewDto view) {
    this.view = view;
  }

  public DecisionGroupByDto getGroupBy() {
    return groupBy;
  }

  public void setGroupBy(DecisionGroupByDto groupBy) {
    this.groupBy = groupBy;
  }

  public DecisionVisualization getVisualization() {
    return visualization;
  }

  public void setVisualization(DecisionVisualization visualization) {
    this.visualization = visualization;
  }

  public DecisionParametersDto getParameters() {
    return parameters;
  }

  public void setParameters(final DecisionParametersDto parameters) {
    this.parameters = parameters;
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

  @Override
  public String toString() {
    return "DecisionReportDataDto{" +
      "decisionDefinitionKey='" + decisionDefinitionKey + '\'' +
      ", decisionDefinitionVersion='" + decisionDefinitionVersion + '\'' +
      ", filter=" + filter +
      ", view=" + view +
      ", groupBy=" + groupBy +
      ", visualization=" + visualization +
      ", parameters=" + parameters +
      '}';
  }
}
