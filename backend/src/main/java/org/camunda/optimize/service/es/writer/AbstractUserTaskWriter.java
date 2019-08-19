/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_OPERATIONS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CLAIM_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@AllArgsConstructor
public abstract class AbstractUserTaskWriter {

  protected final ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  protected List<Map<String, String>> mapToParameterSet(final List<UserTaskInstanceDto> userTaskInstanceDtos) {
    return userTaskInstanceDtos.stream()
      .map(userOperationDto -> (Map<String, String>) objectMapper.convertValue(userOperationDto, Map.class))
      .collect(Collectors.toList());
  }

  protected static String createUpdateUserTaskMetricsScript() {
    // @formatter:off
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
      .put("userTasksField", USER_TASKS)
      .put("userOperationsField", USER_OPERATIONS)
      .put("startDateField", START_DATE)
      .put("endDateField", END_DATE)
      .put("claimDateField", USER_TASK_CLAIM_DATE)
      .put("idleDurationInMsField", USER_TASK_IDLE_DURATION)
      .put("workDurationInMsField", USER_TASK_WORK_DURATION)
      .put("totalDurationInMsField", USER_TASK_TOTAL_DURATION)
      .put("claimTypeValue", "Claim")
      .put("dateFormatPattern", OPTIMIZE_DATE_FORMAT)
      .build()
    );

    return substitutor.replace(
      "if (ctx._source.${userTasksField} != null) {\n" +
        "for (def currentTask : ctx._source.${userTasksField}) {\n" +
         // idle time defaults to 0 if the task has an end field, it get's eventually updated if a claim operation exists
          "if (currentTask.${endDateField} != null) currentTask.${idleDurationInMsField} = 0;\n" +
          // by default work duration equals total duration, it get's eventually updated if a claim operation exists
          "currentTask.${workDurationInMsField} = currentTask.${totalDurationInMsField};\n" +
          "if (currentTask.${userOperationsField} != null) {\n" +
            "def dateFormatter = new SimpleDateFormat(\"${dateFormatPattern}\");\n" +
            "def optionalFirstClaimDate = currentTask.${userOperationsField}.stream()\n" +
                ".filter(userOperation -> \"${claimTypeValue}\".equals(userOperation.type))\n" +
                ".map(userOperation -> userOperation.timestamp)\n" +
                ".min(Comparator.comparing(dateStr -> dateFormatter.parse(dateStr)));\n" +
            "optionalFirstClaimDate.ifPresent(claimDateStr -> {\n" +
              "def claimDate = dateFormatter.parse(claimDateStr);\n" +
              "def claimDateInMs = claimDate.getTime();\n" +
              "currentTask.${claimDateField} = claimDateStr;\n" +
              "def optionalStartDate = Optional.ofNullable(currentTask.${startDateField}).map(dateFormatter::parse);\n" +
              "def optionalEndDate = Optional.ofNullable(currentTask.${endDateField}).map(dateFormatter::parse);\n" +
              "optionalStartDate.ifPresent(startDate -> {\n" +
                  "currentTask.${idleDurationInMsField} = claimDateInMs - startDate.getTime();\n" +
              "});\n" +
              "optionalEndDate.ifPresent(endDate -> {\n" +
                  // if idle time is still null for completed tasks we want it to be set to 0
                  "if (currentTask.${idleDurationInMsField} == null) currentTask.${idleDurationInMsField} = 0;\n" +
                  "currentTask.${workDurationInMsField} = endDate.getTime() - claimDateInMs;\n" +
              "});\n" +
            "});\n" +
          "}\n" +
        "}\n" +
      "}\n"
    );
    // @formatter:on
  }

}
