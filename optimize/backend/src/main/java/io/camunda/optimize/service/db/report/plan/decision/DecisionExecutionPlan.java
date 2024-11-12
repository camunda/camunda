/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.decision;

import static io.camunda.optimize.service.db.report.plan.ReportResultType.MAP;
import static io.camunda.optimize.service.db.report.plan.ReportResultType.NUMBER;
import static io.camunda.optimize.service.db.report.plan.ReportResultType.RAW_DATA;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionDistributedBy.DECISION_DISTRIBUTED_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_EVALUATION_DATE_TIME;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_INPUT_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_MATCHED_RULE;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_NONE;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_OUTPUT_VARIABLE;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionView.DECISION_VIEW_INSTANCE_FREQUENCY;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionView.DECISION_VIEW_RAW_DATA;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.plan.ReportResultType;

public enum DecisionExecutionPlan implements ExecutionPlan {
  DECISION_INSTANCE_FREQUENCY_GROUP_BY_EVALUATION_DATE_TIME(
      DECISION_VIEW_INSTANCE_FREQUENCY,
      DECISION_GROUP_BY_EVALUATION_DATE_TIME,
      DECISION_DISTRIBUTED_BY_NONE,
      MAP),
  DECISION_INSTANCE_FREQUENCY_GROUP_BY_INPUT_VARIABLE(
      DECISION_VIEW_INSTANCE_FREQUENCY,
      DECISION_GROUP_BY_INPUT_VARIABLE,
      DECISION_DISTRIBUTED_BY_NONE,
      MAP),
  DECISION_INSTANCE_FREQUENCY_GROUP_BY_MATCHED_RULE(
      DECISION_VIEW_INSTANCE_FREQUENCY,
      DECISION_GROUP_BY_MATCHED_RULE,
      DECISION_DISTRIBUTED_BY_NONE,
      MAP),
  DECISION_INSTANCE_FREQUENCY_GROUP_BY_NONE(
      DECISION_VIEW_INSTANCE_FREQUENCY,
      DECISION_GROUP_BY_NONE,
      DECISION_DISTRIBUTED_BY_NONE,
      NUMBER),
  DECISION_INSTANCE_FREQUENCY_GROUP_BY_OUTPUT_VARIABLE(
      DECISION_VIEW_INSTANCE_FREQUENCY,
      DECISION_GROUP_BY_OUTPUT_VARIABLE,
      DECISION_DISTRIBUTED_BY_NONE,
      MAP),

  DECISION_RAW_DECISION_INSTANCE_DATA_GROUP_BY_NONE(
      DECISION_VIEW_RAW_DATA, DECISION_GROUP_BY_NONE, DECISION_DISTRIBUTED_BY_NONE, RAW_DATA);

  private final DecisionView view;
  private final DecisionGroupBy groupBy;
  private final DecisionDistributedBy distributedBy;
  private final ReportResultType resultType;
  private final String commandKey;

  DecisionExecutionPlan(
      final DecisionView view,
      final DecisionGroupBy groupBy,
      final DecisionDistributedBy distributedBy,
      final ReportResultType resultType) {
    this.view = view;
    this.groupBy = groupBy;
    this.distributedBy = distributedBy;
    this.resultType = resultType;
    commandKey = buildCommandKey();
  }

  private DecisionExecutionPlan(
      final DecisionView view,
      final DecisionGroupBy groupBy,
      final DecisionDistributedBy distributedBy,
      final ReportResultType resultType,
      final String commandKey) {
    this.view = view;
    this.groupBy = groupBy;
    this.distributedBy = distributedBy;
    this.resultType = resultType;
    this.commandKey = commandKey;
  }

  @Override
  public boolean isRawDataReport() {
    return resultType == RAW_DATA;
  }

  private String buildCommandKey() {
    final DecisionReportDataDto decisionReportDataDto =
        DecisionReportDataDto.builder()
            .groupBy(groupBy.getDto())
            .distributedBy(distributedBy.getDto())
            .view(view.getDecisionViewDto())
            .build();

    return decisionReportDataDto.createCommandKeys().get(0);
  }

  public DecisionView getView() {
    return this.view;
  }

  public DecisionGroupBy getGroupBy() {
    return this.groupBy;
  }

  public DecisionDistributedBy getDistributedBy() {
    return this.distributedBy;
  }

  public ReportResultType getResultType() {
    return this.resultType;
  }

  public String getCommandKey() {
    return this.commandKey;
  }
}
