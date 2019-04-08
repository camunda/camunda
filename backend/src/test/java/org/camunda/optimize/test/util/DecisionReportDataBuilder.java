/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByMatchedRuleDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByEvaluationDateTime;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByInputVariable;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByNone;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByOutputVariable;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionViewDtoCreator.createCountFrequencyView;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionViewDtoCreator.createDecisionRawDataView;

public class DecisionReportDataBuilder {

  private DecisionReportDataType reportDataType;

  private String decisionDefinitionKey;
  private String decisionDefinitionVersion;
  private String variableId;
  private String variableName;
  private GroupByDateUnit dateInterval;

  private List<DecisionFilterDto> filter = new ArrayList<>();

  public static DecisionReportDataBuilder create() {
    return new DecisionReportDataBuilder();
  }

  public DecisionReportDataDto build() {
    DecisionReportDataDto reportData;
    switch (reportDataType) {
      case RAW_DATA:
        reportData = createDecisionReportDataViewRawAsTable(decisionDefinitionKey, decisionDefinitionVersion);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_NONE:
        reportData = createCountFrequencyReportGroupByNone(decisionDefinitionKey, decisionDefinitionVersion);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME:
        reportData = createCountFrequencyReportGroupByEvaluationDate(
          decisionDefinitionKey, decisionDefinitionVersion, dateInterval
        );
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE:
        reportData = createCountFrequencyReportGroupByInputVariable(
          decisionDefinitionKey, decisionDefinitionVersion, variableId, variableName
        );
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE:
        reportData = createCountFrequencyReportGroupByOutputVariable(
          decisionDefinitionKey, decisionDefinitionVersion, variableId, variableName
        );
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE:
        reportData = createCountFrequencyReportGroupByMatchedRule(decisionDefinitionKey, decisionDefinitionVersion);
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

  public DecisionReportDataBuilder setDecisionDefinitionKey(String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionVersion(String decisionDefinitionVersion) {
    this.decisionDefinitionVersion = decisionDefinitionVersion;
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

  public DecisionReportDataBuilder setDateInterval(GroupByDateUnit dateInterval) {
    this.dateInterval = dateInterval;
    return this;
  }

  public DecisionReportDataBuilder setFilter(DecisionFilterDto newFilter) {
    this.filter.add(newFilter);
    return this;
  }

  public DecisionReportDataBuilder setFilter(List<DecisionFilterDto> newFilter) {
    this.filter.addAll(newFilter);
    return this;
  }

  public static DecisionReportDataDto createDecisionReportDataViewRawAsTable(String decisionDefinitionKey,
                                                                             String decisionDefinitionVersion) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersion(decisionDefinitionVersion);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createDecisionRawDataView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByNone());
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByNone(String decisionDefinitionKey,
                                                                             String decisionDefinitionVersion) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersion(decisionDefinitionVersion);
    decisionReportDataDto.setVisualization(DecisionVisualization.NUMBER);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByNone());
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByEvaluationDate(String decisionDefinitionKey,
                                                                                       String decisionDefinitionVersion,
                                                                                       GroupByDateUnit groupByDateUnit) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersion(decisionDefinitionVersion);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByEvaluationDateTime(groupByDateUnit));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByInputVariable(String decisionDefinitionKey,
                                                                                      String decisionDefinitionVersion,
                                                                                      String variableId,
                                                                                      String variableName) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersion(decisionDefinitionVersion);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByInputVariable(variableId, variableName));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByOutputVariable(String decisionDefinitionKey,
                                                                                       String decisionDefinitionVersion,
                                                                                       String variableId,
                                                                                       String variableName) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersion(decisionDefinitionVersion);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByOutputVariable(variableId, variableName));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByMatchedRule(String decisionDefinitionKey,
                                                                                    String decisionDefinitionVersion) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersion(decisionDefinitionVersion);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(new DecisionGroupByMatchedRuleDto());
    return decisionReportDataDto;
  }

}
