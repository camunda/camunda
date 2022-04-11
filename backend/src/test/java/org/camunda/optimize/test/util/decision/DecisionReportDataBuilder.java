/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util.decision;

import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByMatchedRuleDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByEvaluationDateTime;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByInputVariable;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByNone;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByOutputVariable;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionViewDtoCreator.createCountFrequencyView;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionViewDtoCreator.createDecisionRawDataView;

public class DecisionReportDataBuilder {

  private DecisionReportDataType reportDataType;

  private List<ReportDataDefinitionDto> definitions = Collections.singletonList(new ReportDataDefinitionDto());
  private String variableId;
  private String variableName;
  private VariableType variableType;
  private AggregateByDateUnit dateInterval;

  private List<DecisionFilterDto<?>> filter = new ArrayList<>();

  public static DecisionReportDataBuilder create() {
    return new DecisionReportDataBuilder();
  }

  public DecisionReportDataDto build() {
    DecisionReportDataDto reportData;
    switch (reportDataType) {
      case RAW_DATA:
        reportData = createDecisionReportDataViewRawAsTable(definitions);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_NONE:
        reportData = createCountFrequencyReportGroupByNone(definitions);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME:
        reportData = createCountFrequencyReportGroupByEvaluationDate(
          definitions, dateInterval
        );
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE:
        reportData = createCountFrequencyReportGroupByInputVariable(
          definitions, variableId, variableName, variableType
        );
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE:
        reportData = createCountFrequencyReportGroupByOutputVariable(
          definitions, variableId, variableName, variableType
        );
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE:
        reportData = createCountFrequencyReportGroupByMatchedRule(definitions);
        break;
      default:
        throw new IllegalStateException("Unsupported type: " + reportDataType);
    }
    reportData.setFilter(this.filter);
    return reportData;
  }

  public DecisionReportDataBuilder setReportDataType(DecisionReportDataType reportDataType) {
    this.reportDataType = reportDataType;
    return this;
  }

  public DecisionReportDataBuilder definitions(List<ReportDataDefinitionDto> definitions) {
    this.definitions = definitions;
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionKey(String decisionDefinitionKey) {
    this.definitions.get(0).setKey(decisionDefinitionKey);
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionVersion(String decisionDefinitionVersion) {
    this.definitions.get(0).setVersion(decisionDefinitionVersion);
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionVersions(List<String> decisionDefinitionVersions) {
    this.definitions.get(0).setVersions(decisionDefinitionVersions);
    return this;
  }

  public DecisionReportDataBuilder setTenantId(String tenantId) {
    return setTenantIds(Collections.singletonList(tenantId));
  }

  public DecisionReportDataBuilder setTenantIds(List<String> tenantIds) {
    this.definitions.get(0).setTenantIds(tenantIds);
    return this;
  }

  public DecisionReportDataBuilder setVariableId(String variableId) {
    this.variableId = variableId;
    return this;
  }

  public DecisionReportDataBuilder setVariableName(String variableName) {
    this.variableName = variableName;
    return this;
  }

  public DecisionReportDataBuilder setVariableType(VariableType variableType) {
    this.variableType = variableType;
    return this;
  }

  public DecisionReportDataBuilder setDateInterval(AggregateByDateUnit dateInterval) {
    this.dateInterval = dateInterval;
    return this;
  }

  public DecisionReportDataBuilder setFilter(DecisionFilterDto newFilter) {
    this.filter = Collections.singletonList(newFilter);
    return this;
  }

  public DecisionReportDataBuilder setFilter(List<DecisionFilterDto<?>> newFilter) {
    this.filter = newFilter;
    return this;
  }

  public static DecisionReportDataDto createDecisionReportDataViewRawAsTable(List<ReportDataDefinitionDto> definitions) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createDecisionRawDataView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByNone());
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByNone(List<ReportDataDefinitionDto> definitions) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.NUMBER);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByNone());
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByEvaluationDate(List<ReportDataDefinitionDto> definitions,
                                                                                       AggregateByDateUnit groupByDateUnit) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByEvaluationDateTime(groupByDateUnit));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByInputVariable(List<ReportDataDefinitionDto> definitions,
                                                                                      String variableId,
                                                                                      String variableName,
                                                                                      VariableType variableType) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByInputVariable(variableId, variableName, variableType));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByOutputVariable(List<ReportDataDefinitionDto> definitions,
                                                                                       String variableId,
                                                                                       String variableName,
                                                                                       VariableType variableType) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByOutputVariable(variableId, variableName, variableType));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByMatchedRule(List<ReportDataDefinitionDto> definitions) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(new DecisionGroupByMatchedRuleDto());
    return decisionReportDataDto;
  }

}
