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

  /**
   * Returns the flat process instance index name incorporating the ordinal tick. The ordinal tick
   * is mandatory; this overload must be used when an ordinal is known.
   */
  public static String getFlatProcessInstanceIndexAliasName(
      final String processDefinitionKey, final String ordinalTick) {
    if (processDefinitionKey == null) {
      return PROCESS_INSTANCE_MULTI_ALIAS;
    } else {
      return FlatProcessInstanceIndex.constructIndexName(processDefinitionKey, ordinalTick);
    }
  }

  public static String getFlatFlowNodeInstanceIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return FLAT_FLOW_NODE_INSTANCE_MULTI_ALIAS;
    } else {
      return FlatFlowNodeInstanceIndex.constructIndexName(processDefinitionKey);
    }
  }

  /**
   * Returns the flat flow-node-instance index alias name composed of the process definition key and
   * the ordinal tick string (e.g. {@code "20260306-1430"}). Both components are mandatory.
   */
  public static String getFlatFlowNodeInstanceIndexAliasName(
      final String processDefinitionKey, final String ordinalTick) {
    return FlatFlowNodeInstanceIndex.constructIndexName(processDefinitionKey, ordinalTick);
  }

  public static String getFlatIncidentIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return FLAT_INCIDENT_MULTI_ALIAS;
    } else {
      return FlatIncidentIndex.constructIndexName(processDefinitionKey);
    }
  }

  /**
   * Returns the flat incident index alias name composed of the process definition key and the
   * ordinal tick string (e.g. {@code "20260306-1430"}). Both components are mandatory.
   */
  public static String getFlatIncidentIndexAliasName(
      final String processDefinitionKey, final String ordinalTick) {
    return FlatIncidentIndex.constructIndexName(processDefinitionKey, ordinalTick);
  }

  public static String getFlatUserTaskIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return FLAT_USER_TASK_MULTI_ALIAS;
    } else {
      return FlatUserTaskIndex.constructIndexName(processDefinitionKey);
    }
  }

  /**
   * Returns the flat user-task index alias name composed of the process definition key and the
   * ordinal tick string (e.g. {@code "20260306-1430"}). Both components are mandatory.
   */
  public static String getFlatUserTaskIndexAliasName(
      final String processDefinitionKey, final String ordinalTick) {
    return FlatUserTaskIndex.constructIndexName(processDefinitionKey, ordinalTick);
  }

  public static String getFlatVariableIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return FLAT_VARIABLE_MULTI_ALIAS;
    } else {
      return FlatVariableIndex.constructIndexName(processDefinitionKey);
    }
  }

  /**
   * Returns the flat variable index alias name composed of the process definition key and the
   * ordinal tick string (e.g. {@code "20260306-1430"}). Both components are mandatory.
   */
  public static String getFlatVariableIndexAliasName(
      final String processDefinitionKey, final String ordinalTick) {
    return FlatVariableIndex.constructIndexName(processDefinitionKey, ordinalTick);
  }

  public static String getPreFlattenedIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return PRE_FLATTENED_MULTI_ALIAS;
    } else {
      return PreFlattenedIndex.constructIndexName(processDefinitionKey);
    }
  }
}
