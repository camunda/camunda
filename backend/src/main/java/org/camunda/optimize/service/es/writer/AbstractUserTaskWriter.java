/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE_OPERATIONS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CLAIM_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@AllArgsConstructor
public abstract class AbstractUserTaskWriter<T extends OptimizeDto> {
  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  protected List<Map<String, String>> mapToParameterSet(final List<UserTaskInstanceDto> userTaskInstanceDtos) {
    return userTaskInstanceDtos.stream()
      .map(userTaskInstanceDto -> (Map<String, String>) objectMapper.convertValue(userTaskInstanceDto, Map.class))
      .collect(Collectors.toList());
  }

  protected Script createUpdateScript(List<UserTaskInstanceDto> userTasks) {
    final ImmutableMap<String, Object> scriptParameters = ImmutableMap.of(USER_TASKS, mapToParameterSet(userTasks));
    return createDefaultScript(createInlineUpdateScript(), scriptParameters);
  }

  protected abstract String createInlineUpdateScript();

  protected static String createUpdateUserTaskMetricsScript() {

    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("userTasksField", USER_TASKS)
        .put("assigneeOperationsField", USER_TASK_ASSIGNEE_OPERATIONS)
        .put("startDateField", START_DATE)
        .put("endDateField", END_DATE)
        .put("claimDateField", USER_TASK_CLAIM_DATE)
        .put("assigneeOperationTypeField", ASSIGNEE_OPERATION_TYPE)
        .put("idleDurationInMsField", USER_TASK_IDLE_DURATION)
        .put("workDurationInMsField", USER_TASK_WORK_DURATION)
        .put("totalDurationInMsField", USER_TASK_TOTAL_DURATION)
        .put("operationTypeAddValue", "add")
        .put("dateFormatPattern", OPTIMIZE_DATE_FORMAT)
        .build()
    );

    // @formatter:off
    return substitutor.replace(
      "if (ctx._source.${userTasksField} != null) {\n" +
        "for (def currentTask : ctx._source.${userTasksField}) {\n" +
         // idle time defaults to 0 if the task has an end field, it get's eventually updated if a claim operation exists
          "if (currentTask.${endDateField} != null) currentTask.${idleDurationInMsField} = 0;\n" +
          // by default work duration equals total duration, it get's eventually updated if a claim operation exists
          "currentTask.${workDurationInMsField} = currentTask.${totalDurationInMsField};\n" +
          "if (currentTask.${assigneeOperationsField} != null) {\n" +
            "def dateFormatter = new SimpleDateFormat(\"${dateFormatPattern}\");\n" +

            "def optionalFirstClaimDate = currentTask.${assigneeOperationsField}.stream()\n" +
                ".filter(operation -> \"${operationTypeAddValue}\".equals(operation.operationType))\n" +
                ".map(operation -> operation.timestamp) \n" +
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

  protected void addImportUserTaskToRequest(final BulkRequest bulkRequest,
                                            Map.Entry<String, List<T>> activityInstanceEntry) {
    if (!activityInstanceEntry.getValue().stream().allMatch(dto -> dto instanceof UserTaskInstanceDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    final List<UserTaskInstanceDto> userTasks = (List<UserTaskInstanceDto>) (List<?>) activityInstanceEntry.getValue();
    final String activiyInstanceId = activityInstanceEntry.getKey();

    final Script updateScript = createUpdateScript(userTasks);

    final UserTaskInstanceDto firstUserTaskInstance = userTasks.stream().findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("No user tasks to import provided"));
    final ProcessInstanceDto procInst = new ProcessInstanceDto()
      .setProcessInstanceId(firstUserTaskInstance.getProcessInstanceId())
      .setEngine(firstUserTaskInstance.getEngine())
      .setUserTasks(userTasks);
    String newEntryIfAbsent = null;
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
    } catch (JsonProcessingException e) {
      String reason = String.format(
        "Error while processing JSON for user tasks with ID [%s].",
        activiyInstanceId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    UpdateRequest request = new UpdateRequest()
      .index(PROCESS_INSTANCE_INDEX_NAME)
      .id(activiyInstanceId)
      .script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

}
