/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util.decision;

import com.google.common.collect.Lists;
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

  private String decisionDefinitionKey;
  private List<String> decisionDefinitionVersions = Collections.emptyList();
  private List<String> tenantIds = new ArrayList<>(Collections.singleton(null));
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
        reportData = createDecisionReportDataViewRawAsTable(decisionDefinitionKey, decisionDefinitionVersions);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_NONE:
        reportData = createCountFrequencyReportGroupByNone(decisionDefinitionKey, decisionDefinitionVersions);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME:
        reportData = createCountFrequencyReportGroupByEvaluationDate(
          decisionDefinitionKey, decisionDefinitionVersions, dateInterval
        );
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE:
        reportData = createCountFrequencyReportGroupByInputVariable(
          decisionDefinitionKey, decisionDefinitionVersions, variableId, variableName, variableType
        );
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE:
        reportData = createCountFrequencyReportGroupByOutputVariable(
          decisionDefinitionKey, decisionDefinitionVersions, variableId, variableName, variableType
        );
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE:
        reportData = createCountFrequencyReportGroupByMatchedRule(decisionDefinitionKey, decisionDefinitionVersions);
        break;
      default:
        throw new IllegalStateException("Unsupported type: " + reportDataType);
    }
    reportData.setFilter(this.filter);
    reportData.setTenantIds(this.tenantIds);
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
    this.decisionDefinitionVersions = Lists.newArrayList(decisionDefinitionVersion);
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionVersions(List<String> decisionDefinitionVersions) {
    this.decisionDefinitionVersions = decisionDefinitionVersions;
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

  public DecisionReportDataBuilder setTenantId(String tenantId) {
    this.tenantIds = Collections.singletonList(tenantId);
    return this;
  }

  public DecisionReportDataBuilder setTenantIds(List<String> tenantIds) {
    this.tenantIds = tenantIds;
    return this;
  }

  public static DecisionReportDataDto createDecisionReportDataViewRawAsTable(String decisionDefinitionKey,
                                                                             List<String> decisionDefinitionVersions) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersions(decisionDefinitionVersions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createDecisionRawDataView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByNone());
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByNone(String decisionDefinitionKey,
                                                                             List<String> decisionDefinitionVersions) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersions(decisionDefinitionVersions);
    decisionReportDataDto.setVisualization(DecisionVisualization.NUMBER);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByNone());
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByEvaluationDate(String decisionDefinitionKey,
                                                                                       List<String> decisionDefinitionVersions,
                                                                                       AggregateByDateUnit groupByDateUnit) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersions(decisionDefinitionVersions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByEvaluationDateTime(groupByDateUnit));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByInputVariable(String decisionDefinitionKey,
                                                                                      List<String> decisionDefinitionVersions,
                                                                                      String variableId,
                                                                                      String variableName,
                                                                                      VariableType variableType) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersions(decisionDefinitionVersions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByInputVariable(variableId, variableName, variableType));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByOutputVariable(String decisionDefinitionKey,
                                                                                       List<String> decisionDefinitionVersions,
                                                                                       String variableId,
                                                                                       String variableName,
                                                                                       VariableType variableType) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersions(decisionDefinitionVersions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByOutputVariable(variableId, variableName, variableType));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByMatchedRule(String decisionDefinitionKey,
                                                                                    List<String> decisionDefinitionVersions) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersions(decisionDefinitionVersions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(new DecisionGroupByMatchedRuleDto());
    return decisionReportDataDto;
  }

}
