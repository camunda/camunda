/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.elasticsearch.script.Script;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DurationScriptUtil {

  public static Script getDurationScript(final long currRequestDateInMs,
                                         final String durationFieldName,
                                         final String referenceDateFieldName) {
    final Map<String, Object> params = new HashMap<>();

    return createDefaultScriptWithPrimitiveParams(
      getDurationCalculationScriptPart(
        params,
        currRequestDateInMs,
        durationFieldName,
        referenceDateFieldName
      )
        + " return result;",
      params
    );

  }

  public static Script getUserTaskDurationScript(final long currRequestDateInMs,
                                                 final String durationFieldName) {
    final Map<String, Object> params = new HashMap<>();

    return createDefaultScriptWithPrimitiveParams(
      getUserTaskDurationCalculationScriptPart(params, currRequestDateInMs, durationFieldName)
        + " return result;",
      params
    );

  }

  public static Script getDurationFilterScript(final long currRequestDateInMs,
                                               final String durationFieldName,
                                               final String referenceDateFieldName,
                                               final DurationFilterDataDto durationFilterDto) {
    final Map<String, Object> params = new HashMap<>();
    params.put("filterDuration", getFilterDuration(durationFilterDto));
    return createDefaultScriptWithPrimitiveParams(
      // All duration filters operate on totalDuration
      // --> no specific userTask calculations needed, can use the general duration script
      getDurationCalculationScriptPart(
        params,
        currRequestDateInMs,
        durationFieldName,
        referenceDateFieldName
      )
        + " return (result != null " +
        "&& result " + durationFilterDto.getOperator().getId() + " params['filterDuration'])" +
        " || (" + durationFilterDto.isIncludeNull() + " && result == null)",
      params
    );
  }

  private static long getFilterDuration(final DurationFilterDataDto durationFilterDto) {
    return ChronoUnit.valueOf(durationFilterDto.getUnit().name())
      .getDuration()
      .toMillis() * durationFilterDto.getValue();
  }

  private static String getDurationCalculationScriptPart(final Map<String, Object> params,
                                                         final long currRequestDateInMs,
                                                         final String durationFieldName,
                                                         final String referenceDateFieldName) {
    params.put("currRequestDateInMs", currRequestDateInMs);
    params.put("durFieldName", durationFieldName);
    params.put("refDateFieldName", referenceDateFieldName);

    // @formatter:off
    return "Long result; " +
      "if (doc[params.durFieldName].empty && !doc[params.refDateFieldName].empty) {" +
        "result = params.currRequestDateInMs - doc[params.refDateFieldName].value.toInstant().toEpochMilli()" +
      "} else { " +
        "result = !doc[params.durFieldName].empty ? doc[params.durFieldName].value : null " +
      "} ";
    // @formatter:on
  }

  private static String getUserTaskDurationCalculationScriptPart(final Map<String, Object> params,
                                                                 final long currRequestDateInMs,
                                                                 final String durationFieldName) {
    params.put("currRequestDateInMs", currRequestDateInMs);
    params.put("startDateFieldName", FLOW_NODE_INSTANCES + "." + FLOW_NODE_START_DATE);
    params.put("endDateFieldName", FLOW_NODE_INSTANCES + "." + FLOW_NODE_END_DATE);
    params.put("assigneeFieldName", FLOW_NODE_INSTANCES + "." + USER_TASK_ASSIGNEE);
    params.put("totalDurationFieldName", FLOW_NODE_INSTANCES + "." + FLOW_NODE_TOTAL_DURATION);
    params.put("idleDurationFieldName", FLOW_NODE_INSTANCES + "." + USER_TASK_IDLE_DURATION);
    params.put("workDurationFieldName", FLOW_NODE_INSTANCES + "." + USER_TASK_WORK_DURATION);

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
    return "" +
        "def result = 0;\n" +
        "def hasStarted = !doc[params.startDateFieldName].empty && doc[params.startDateFieldName].value != null;\n" +
        "def hasEnded = !doc[params.endDateFieldName].empty && doc[params.endDateFieldName].value != null;\n" +
        "def isRunning = hasStarted && !hasEnded;\n" +
        "def isCurrentlyIdle = doc[params.assigneeFieldName].empty || doc[params.assigneeFieldName].value == null;\n" +
        "def hasTotalDuration = !doc[params.totalDurationFieldName].empty && doc[params.totalDurationFieldName].value != null;\n" +
        "def hasIdleDuration = !doc[params.idleDurationFieldName].empty && doc[params.idleDurationFieldName].value != null;\n" +
        "def hasWorkDuration = !doc[params.workDurationFieldName].empty && doc[params.workDurationFieldName].value != null;\n" +
        "def startTimeInMs = hasStarted " +
            "? doc[params.startDateFieldName].value.toInstant().toEpochMilli()" +
            ": 0;\n" +
        "def endTimeInMs = hasEnded " +
            "? doc[params.endDateFieldName].value.toInstant().toEpochMilli()" +
            ": 0;\n" +

      // Also ensure total duration is calculated
        "def currTotalDuration = 0;\n" +
        "if(hasEnded) {\n" +
          "if(hasTotalDuration) {\n" +
            "currTotalDuration = doc[params.totalDurationFieldName].value;\n" +
          "} else {\n" +
            "currTotalDuration = endTimeInMs - startTimeInMs;\n" +
          "}\n" +
        "} else if(isRunning) {\n" +
          "currTotalDuration = params.currRequestDateInMs - startTimeInMs;\n" +
        "}\n";
    // @formatter:on
  }

  private static String getUserTaskTotalDurationCalculationScriptPart(final String variableDefinitionScript) {
    return variableDefinitionScript + "result = currTotalDuration;";
  }

  private static String getUserTaskIdleDurationCalculationScriptPart(final String variableDefinitionScript) {
    // @formatter:off
    return variableDefinitionScript +
      // We require idle time and usertask is finished or
      // We require idle time and userTask is currently working --> idle duration have already been calculated during import
      "if(hasEnded || !isCurrentlyIdle){\n" +
        "result = doc[params.idleDurationFieldName].value;" +
      "}\n" +

      // UserTask is running and is in a state that requires us to recalculate the current idle time
      // The calculation is based on: totalDuration == workDuration + idleDuration --> idle = total - work
      "else {\n" +
        "def durationToAdd = 0;\n" +
        "def currentIdleDuration = hasIdleDuration ? doc[params.idleDurationFieldName].value : 0;\n" +
        "def workDuration = hasWorkDuration ? doc[params.workDurationFieldName].value : 0;\n" +
        "durationToAdd = currTotalDuration - workDuration;\n" +
        "result = currentIdleDuration + durationToAdd;\n" +
      "}\n";
    // @formatter:on
  }

  private static String getUserTaskWorkDurationCalculationScriptPart(final String variableDefinitionScript) {
    // @formatter:off
    return variableDefinitionScript +
      // We require work time and usertask is finished --> all durations have already been calculated during import
      "if(hasEnded){\n" +
        "result = doc[params.workDurationFieldName].value;" +
      "}\n" +

      // We require work time and userTask is currently idle, this can happen in two scenarios:
      "else if (isCurrentlyIdle) {\n" +
        "if (hasWorkDuration) {\n" +
          // 1) claims have occurred and hence work duration already been calculated during import
          "result = doc[params.workDurationFieldName].value;\n" +
        "} else {\n" +
          // 2) no claim/unclaims have occurred yet, the duration field will still be null.
          // When usertask has never been claimed, all time counts as idle time and worktime should be null
          "result = null ;\n" +
        "}\n" +
      "} " +

      // UserTask is running and is in a state that requires us to recalculate the current work time
      // The calculation is based on: totalDuration == workDuration + idleDuration --> work = total - idle
      "else {\n" +
        "def durationToAdd = 0;\n" +
        "def currentWorkDuration = hasWorkDuration ? doc[params.workDurationFieldName].value : 0;\n" +
        "def idleDuration = hasIdleDuration ? doc[params.idleDurationFieldName].value : 0;\n" +
        "durationToAdd = currTotalDuration - idleDuration;\n" +
        "result = currentWorkDuration + durationToAdd;\n" +
      "}\n";
    // @formatter:on
  }

}
