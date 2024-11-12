/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.util;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.json;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;

public final class DurationScriptUtilOS {

  private DurationScriptUtilOS() {}

  public static Script getDurationScript(
      final long currRequestDateInMs,
      final String durationFieldName,
      final String referenceDateFieldName) {
    final Map<String, JsonData> params = new HashMap<>();
    return OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
        getDurationCalculationScriptPart(
                params, currRequestDateInMs, durationFieldName, referenceDateFieldName)
            + " return result;",
        params);
  }

  public static Script getUserTaskDurationScript(
      final long currRequestDateInMs, final String durationFieldName) {
    final Map<String, JsonData> params =
        Map.of(
            "currRequestDateInMs",
            json(currRequestDateInMs),
            "startDateFieldName",
            json(FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE),
            "endDateFieldName",
            json(FLOW_NODE_INSTANCES + "." + FLOW_NODE_END_DATE),
            "assigneeFieldName",
            json(FLOW_NODE_INSTANCES + "." + USER_TASK_ASSIGNEE),
            "totalDurationFieldName",
            json(FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION),
            "idleDurationFieldName",
            json(FLOW_NODE_INSTANCES + "." + USER_TASK_IDLE_DURATION),
            "workDurationFieldName",
            json(FLOW_NODE_INSTANCES + "." + USER_TASK_WORK_DURATION));

    return OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
        getUserTaskDurationCalculationScriptPart(durationFieldName) + " return result;", params);
  }

  private static String getUserTaskDurationCalculationScriptPart(final String durationFieldName) {
    final String variableDefinitionScript =
        getUserTaskDurationCalculationVariableDefinitionScriptPart();
    if (durationFieldName.contains(FLOW_NODE_TOTAL_DURATION)) {
      return getUserTaskTotalDurationCalculationScriptPart(variableDefinitionScript);
    } else if (durationFieldName.contains(USER_TASK_IDLE_DURATION)) {
      return getUserTaskIdleDurationCalculationScriptPart(variableDefinitionScript);
    } else {
      return getUserTaskWorkDurationCalculationScriptPart(variableDefinitionScript);
    }
  }

  private static String getUserTaskDurationCalculationVariableDefinitionScriptPart() {
    // @formatter:off
    // Also ensure total duration is calculated
    return """
        def result = 0;
        def hasStarted = !doc[params.startDateFieldName].empty && doc[params.startDateFieldName].value != null;
        def hasEnded = !doc[params.endDateFieldName].empty && doc[params.endDateFieldName].value != null;
        def isRunning = hasStarted && !hasEnded;
        def isCurrentlyIdle = doc[params.assigneeFieldName].empty || doc[params.assigneeFieldName].value == null;
        def hasTotalDuration = !doc[params.totalDurationFieldName].empty && doc[params.totalDurationFieldName].value != null;
        def hasIdleDuration = !doc[params.idleDurationFieldName].empty && doc[params.idleDurationFieldName].value != null;
        def hasWorkDuration = !doc[params.workDurationFieldName].empty && doc[params.workDurationFieldName].value != null;
        def startTimeInMs = hasStarted ? doc[params.startDateFieldName].value.toInstant().toEpochMilli(): 0;
        def endTimeInMs = hasEnded ? doc[params.endDateFieldName].value.toInstant().toEpochMilli(): 0;
        def currTotalDuration = 0;
        if(hasEnded) {
          if(hasTotalDuration) {
            currTotalDuration = doc[params.totalDurationFieldName].value;
          } else {
            currTotalDuration = endTimeInMs - startTimeInMs;
          }
        } else if(isRunning) {
          currTotalDuration = params.currRequestDateInMs - startTimeInMs;
        }
        """;
    // @formatter:on
  }

  private static String getUserTaskTotalDurationCalculationScriptPart(
      final String variableDefinitionScript) {
    return variableDefinitionScript + "result = currTotalDuration;";
  }

  private static String getUserTaskIdleDurationCalculationScriptPart(
      final String variableDefinitionScript) {
    // @formatter:off
    return variableDefinitionScript
        +
        // We require idle time and usertask is finished or
        // We require idle time and userTask is currently working
        // In both cases, idle duration should already have been calculated during import.
        // Additional hasIdleDuration just in case of data inconsistencies.
        "if(hasIdleDuration && (hasEnded || !isCurrentlyIdle)){\n"
        + "  result = doc[params.idleDurationFieldName].value;"
        + "}\n"
        +

        // UserTask is running and is in a state that requires us to recalculate the current idle
        // time
        // The calculation is based on: totalDuration == workDuration + idleDuration --> idle =
        // total - work
        "else {\n"
        + "  def durationToAdd = 0;\n"
        + "  def currentIdleDuration = hasIdleDuration ? doc[params.idleDurationFieldName].value : 0;\n"
        + "  def workDuration = hasWorkDuration ? doc[params.workDurationFieldName].value : 0;\n"
        + "  durationToAdd = currTotalDuration - workDuration;\n"
        + "  result = currentIdleDuration + durationToAdd;\n"
        + "}\n";
    // @formatter:on
  }

  private static String getUserTaskWorkDurationCalculationScriptPart(
      final String variableDefinitionScript) {
    // @formatter:off
    return variableDefinitionScript
        +
        // We require work time and usertask is finished
        // All durations have already been calculated during import,
        // additional hasWorkDuration in case of data inconsistencies
        "if(hasWorkDuration && hasEnded){\n"
        + "  result = doc[params.workDurationFieldName].value;"
        + "}\n"
        +

        // We require work time and userTask is currently idle, this can happen in two scenarios:
        "else if (isCurrentlyIdle) {\n"
        + "  if (hasWorkDuration) {\n"
        +
        // 1) claims have occurred and hence work duration already been calculated during import
        "    result = doc[params.workDurationFieldName].value;\n"
        + "  } else {\n"
        +
        // 2) no claim/unclaims have occurred yet, the duration field will still be null.
        // When usertask has never been claimed, all time counts as idle time and worktime should be
        // null
        "    result = null ;\n"
        + "  }\n"
        + "} "
        +

        // UserTask is running and is in a state that requires us to recalculate the current work
        // time
        // The calculation is based on: totalDuration == workDuration + idleDuration --> work =
        // total - idle
        "else {\n"
        + "  def durationToAdd = 0;\n"
        + "  def currentWorkDuration = hasWorkDuration ? doc[params.workDurationFieldName].value : 0;\n"
        + "  def idleDuration = hasIdleDuration ? doc[params.idleDurationFieldName].value : 0;\n"
        + "  durationToAdd = currTotalDuration - idleDuration;\n"
        + "  result = currentWorkDuration + durationToAdd;\n"
        + "}\n";
    // @formatter:on
  }

  public static Script getDurationFilterScript(
      final long currRequestDateInMs,
      final String durationFieldName,
      final String referenceDateFieldName,
      final DurationFilterDataDto durationFilterDto) {
    final Map<String, JsonData> params = new HashMap<>();
    params.put("filterDuration", JsonData.of(getFilterDuration(durationFilterDto)));
    return OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
        // All duration filters operate on totalDuration
        // --> no specific userTask calculations needed, can use the general duration script
        getDurationCalculationScriptPart(
                params, currRequestDateInMs, durationFieldName, referenceDateFieldName)
            + " return (result != null && result "
            + durationFilterDto.getOperator().getId()
            + " params['filterDuration']) || ("
            + durationFilterDto.isIncludeNull()
            + " && result == null)",
        params);
  }

  private static long getFilterDuration(final DurationFilterDataDto durationFilterDto) {
    return ChronoUnit.valueOf(durationFilterDto.getUnit().name()).getDuration().toMillis()
        * durationFilterDto.getValue();
  }

  private static String getDurationCalculationScriptPart(
      final Map<String, JsonData> params,
      final long currRequestDateInMs,
      final String durationFieldName,
      final String referenceDateFieldName) {
    params.put("currRequestDateInMs", JsonData.of(currRequestDateInMs));
    params.put("durFieldName", JsonData.of(durationFieldName));
    params.put("refDateFieldName", JsonData.of(referenceDateFieldName));

    return """
        Long result;
        if (doc[params.durFieldName].empty && !doc[params.refDateFieldName].empty) {
          result = params.currRequestDateInMs - doc[params.refDateFieldName].value.toInstant().toEpochMilli()
        } else {
          result = !doc[params.durFieldName].empty ? doc[params.durFieldName].value : null
        }
        """;
  }
}
