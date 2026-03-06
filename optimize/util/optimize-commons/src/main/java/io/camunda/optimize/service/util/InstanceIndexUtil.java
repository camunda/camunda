/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.FLAT_FLOW_NODE_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.FLAT_INCIDENT_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.FLAT_USER_TASK_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.FLAT_VARIABLE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.PRE_FLATTENED_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.db.schema.index.FlatFlowNodeInstanceIndex;
import io.camunda.optimize.service.db.schema.index.FlatIncidentIndex;
import io.camunda.optimize.service.db.schema.index.FlatProcessInstanceIndex;
import io.camunda.optimize.service.db.schema.index.FlatUserTaskIndex;
import io.camunda.optimize.service.db.schema.index.FlatVariableIndex;
import io.camunda.optimize.service.db.schema.index.PreFlattenedIndex;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;

public final class InstanceIndexUtil {

  private InstanceIndexUtil() {}

  public static String[] getDecisionInstanceIndexAliasName(
      final DecisionReportDataDto reportDataDto) {
    // for decision reports only one (the first) definition is supported
    return reportDataDto.getDefinitions().stream()
        .findFirst()
        .map(ReportDataDefinitionDto::getKey)
        .map(InstanceIndexUtil::getDecisionInstanceIndexAliasName)
        .map(value -> new String[] {value})
        .orElse(new String[] {DECISION_INSTANCE_MULTI_ALIAS});
  }

  public static String getDecisionInstanceIndexAliasName(final String decisionDefinitionKey) {
    if (decisionDefinitionKey == null) {
      return DECISION_INSTANCE_MULTI_ALIAS;
    } else {
      return DecisionInstanceIndex.constructIndexName(decisionDefinitionKey);
    }
  }

  public static String[] getProcessInstanceIndexAliasNames(
      final ProcessReportDataDto reportDataDto) {
    if (reportDataDto.isManagementReport()) {
      return new String[] {PROCESS_INSTANCE_MULTI_ALIAS};
    }
    return !reportDataDto.getDefinitions().isEmpty()
        ? reportDataDto.getDefinitions().stream()
            .map(ReportDataDefinitionDto::getKey)
            .map(InstanceIndexUtil::getProcessInstanceIndexAliasName)
            .toArray(String[]::new)
        : new String[] {PROCESS_INSTANCE_MULTI_ALIAS};
  }

  public static String getProcessInstanceIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return PROCESS_INSTANCE_MULTI_ALIAS;
    } else {
      return ProcessInstanceIndex.constructIndexName(processDefinitionKey);
    }
  }

  public static String getFlatProcessInstanceIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return PROCESS_INSTANCE_MULTI_ALIAS;
    } else {
      return FlatProcessInstanceIndex.constructIndexName(processDefinitionKey);
    }
  }

  public static String getFlatFlowNodeInstanceIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return FLAT_FLOW_NODE_INSTANCE_MULTI_ALIAS;
    } else {
      return FlatFlowNodeInstanceIndex.constructIndexName(processDefinitionKey);
    }
  }

  public static String getFlatIncidentIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return FLAT_INCIDENT_MULTI_ALIAS;
    } else {
      return FlatIncidentIndex.constructIndexName(processDefinitionKey);
    }
  }

  public static String getFlatUserTaskIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return FLAT_USER_TASK_MULTI_ALIAS;
    } else {
      return FlatUserTaskIndex.constructIndexName(processDefinitionKey);
    }
  }

  public static String getFlatVariableIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return FLAT_VARIABLE_MULTI_ALIAS;
    } else {
      return FlatVariableIndex.constructIndexName(processDefinitionKey);
    }
  }

  public static String getPreFlattenedIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return PRE_FLATTENED_MULTI_ALIAS;
    } else {
      return PreFlattenedIndex.constructIndexName(processDefinitionKey);
    }
  }
}
