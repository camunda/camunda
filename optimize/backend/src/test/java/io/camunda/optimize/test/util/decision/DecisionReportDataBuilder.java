/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util.decision;

import static io.camunda.optimize.service.db.report.interpreter.util.DecisionGroupByDtoCreator.createGroupDecisionByEvaluationDateTime;
import static io.camunda.optimize.service.db.report.interpreter.util.DecisionGroupByDtoCreator.createGroupDecisionByInputVariable;
import static io.camunda.optimize.service.db.report.interpreter.util.DecisionGroupByDtoCreator.createGroupDecisionByNone;
import static io.camunda.optimize.service.db.report.interpreter.util.DecisionGroupByDtoCreator.createGroupDecisionByOutputVariable;
import static io.camunda.optimize.service.db.report.interpreter.util.DecisionViewDtoCreator.createCountFrequencyView;
import static io.camunda.optimize.service.db.report.interpreter.util.DecisionViewDtoCreator.createDecisionRawDataView;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByMatchedRuleDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DecisionReportDataBuilder {

  private DecisionReportDataType reportDataType;

  private List<ReportDataDefinitionDto> definitions =
      Collections.singletonList(new ReportDataDefinitionDto());
  private String variableId;
  private String variableName;
  private VariableType variableType;
  private AggregateByDateUnit dateInterval;

  private List<DecisionFilterDto<?>> filter = new ArrayList<>();

  public static DecisionReportDataBuilder create() {
    return new DecisionReportDataBuilder();
  }

  public DecisionReportDataDto build() {
    final DecisionReportDataDto reportData;
    switch (reportDataType) {
      case RAW_DATA:
        reportData = createDecisionReportDataViewRawAsTable(definitions);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_NONE:
        reportData = createCountFrequencyReportGroupByNone(definitions);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME:
        reportData = createCountFrequencyReportGroupByEvaluationDate(definitions, dateInterval);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE:
        reportData =
            createCountFrequencyReportGroupByInputVariable(
                definitions, variableId, variableName, variableType);
        break;
      case COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE:
        reportData =
            createCountFrequencyReportGroupByOutputVariable(
                definitions, variableId, variableName, variableType);
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

  public DecisionReportDataBuilder setReportDataType(final DecisionReportDataType reportDataType) {
    this.reportDataType = reportDataType;
    return this;
  }

  public DecisionReportDataBuilder definitions(final List<ReportDataDefinitionDto> definitions) {
    this.definitions = definitions;
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionKey(final String decisionDefinitionKey) {
    this.definitions.get(0).setKey(decisionDefinitionKey);
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionVersion(
      final String decisionDefinitionVersion) {
    this.definitions.get(0).setVersion(decisionDefinitionVersion);
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionVersions(
      final List<String> decisionDefinitionVersions) {
    this.definitions.get(0).setVersions(decisionDefinitionVersions);
    return this;
  }

  public DecisionReportDataBuilder setTenantId(final String tenantId) {
    return setTenantIds(Collections.singletonList(tenantId));
  }

  public DecisionReportDataBuilder setTenantIds(final List<String> tenantIds) {
    this.definitions.get(0).setTenantIds(tenantIds);
    return this;
  }

  public DecisionReportDataBuilder setVariableId(final String variableId) {
    this.variableId = variableId;
    return this;
  }

  public DecisionReportDataBuilder setVariableName(final String variableName) {
    this.variableName = variableName;
    return this;
  }

  public DecisionReportDataBuilder setVariableType(final VariableType variableType) {
    this.variableType = variableType;
    return this;
  }

  public DecisionReportDataBuilder setDateInterval(final AggregateByDateUnit dateInterval) {
    this.dateInterval = dateInterval;
    return this;
  }

  public DecisionReportDataBuilder setFilter(final DecisionFilterDto newFilter) {
    this.filter = Collections.singletonList(newFilter);
    return this;
  }

  public DecisionReportDataBuilder setFilter(final List<DecisionFilterDto<?>> newFilter) {
    this.filter = newFilter;
    return this;
  }

  public static DecisionReportDataDto createDecisionReportDataViewRawAsTable(
      final List<ReportDataDefinitionDto> definitions) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createDecisionRawDataView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByNone());
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByNone(
      final List<ReportDataDefinitionDto> definitions) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.NUMBER);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByNone());
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByEvaluationDate(
      final List<ReportDataDefinitionDto> definitions, final AggregateByDateUnit groupByDateUnit) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByEvaluationDateTime(groupByDateUnit));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByInputVariable(
      final List<ReportDataDefinitionDto> definitions,
      final String variableId,
      final String variableName,
      final VariableType variableType) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(
        createGroupDecisionByInputVariable(variableId, variableName, variableType));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByOutputVariable(
      final List<ReportDataDefinitionDto> definitions,
      final String variableId,
      final String variableName,
      final VariableType variableType) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(
        createGroupDecisionByOutputVariable(variableId, variableName, variableType));
    return decisionReportDataDto;
  }

  private static DecisionReportDataDto createCountFrequencyReportGroupByMatchedRule(
      final List<ReportDataDefinitionDto> definitions) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDefinitions(definitions);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createCountFrequencyView());
    decisionReportDataDto.setGroupBy(new DecisionGroupByMatchedRuleDto());
    return decisionReportDataDto;
  }
}
