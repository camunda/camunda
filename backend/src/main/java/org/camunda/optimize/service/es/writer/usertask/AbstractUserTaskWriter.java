/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.usertask;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.writer.AbstractProcessInstanceDataWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.importing.IdentityLinkLogOperationType.CLAIM_OPERATION_TYPE;
import static org.camunda.optimize.dto.optimize.importing.IdentityLinkLogOperationType.UNCLAIM_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE_OPERATIONS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public abstract class AbstractUserTaskWriter extends AbstractProcessInstanceDataWriter<FlowNodeInstanceDto> {
  protected final ObjectMapper objectMapper;

  protected AbstractUserTaskWriter(final OptimizeElasticsearchClient esClient,
                                   final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                   final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  protected abstract String createInlineUpdateScript();

  protected static String createUpdateUserTaskMetricsScript() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("flowNodesField", FLOW_NODE_INSTANCES)
        .put("flowNodeTypeField", FLOW_NODE_TYPE)
        .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
        .put("assigneeOperationsField", USER_TASK_ASSIGNEE_OPERATIONS)
        .put("startDateField", FLOW_NODE_START_DATE)
        .put("endDateField", FLOW_NODE_END_DATE)
        .put("idleDurationInMsField", USER_TASK_IDLE_DURATION)
        .put("workDurationInMsField", USER_TASK_WORK_DURATION)
        .put("totalDurationInMsField", FLOW_NODE_TOTAL_DURATION)
        .put("canceledField", FLOW_NODE_CANCELED)
        .put("operationTypeClaim", CLAIM_OPERATION_TYPE.getId())
        .put("operationTypeUnclaim", UNCLAIM_OPERATION_TYPE.getId())
        .put("dateFormatPattern", OPTIMIZE_DATE_FORMAT)
        .build()
    );

    // @formatter:off
    return substitutor.replace(
      "if (ctx._source.${flowNodesField} != null) {\n" +
        "def dateFormatter = new SimpleDateFormat(\"${dateFormatPattern}\");\n" +

        "for (def currentTask : ctx._source.${flowNodesField}) {\n" +
          // Ignore any flowNodes that aren't userTasks
          "if(!currentTask.${flowNodeTypeField}.equalsIgnoreCase(\"${userTaskFlowNodeType}\")){\n" +
            "continue;\n" +
          "}\n" +
        
          "def totalWorkTimeInMs = 0;\n" +
          "def totalIdleTimeInMs = 0;\n" +
          "def workTimeHasChanged = false;\n" +
          "def idleTimeHasChanged = false;\n" +

          "if (currentTask.${assigneeOperationsField} != null && !currentTask.${assigneeOperationsField}.isEmpty()) {\n" +
            // Collect all timestamps of unclaim operations, counting the startDate as the first and the endDate as the last unclaim
            "def allUnclaimTimestamps = currentTask.${assigneeOperationsField}.stream()\n" +
              ".filter(operation -> \"${operationTypeUnclaim}\".equals(operation.operationType))\n" +
              ".map(operation -> operation.timestamp)\n" +
              ".map(dateFormatter::parse)" +
              ".collect(Collectors.toList());\n" +
            "Optional.ofNullable(currentTask.${startDateField})" +
              ".map(dateFormatter::parse)\n" +
              ".ifPresent(startDate -> allUnclaimTimestamps.add(startDate));\n" +
            "Optional.ofNullable(currentTask.${endDateField})" +
              ".map(dateFormatter::parse)\n" +
              ".ifPresent(endDate -> allUnclaimTimestamps.add(endDate));\n" +
            "allUnclaimTimestamps.sort(Comparator.naturalOrder());\n" +

            // Collect all timestamps of claim operations
            "def allClaimTimestamps = currentTask.${assigneeOperationsField}.stream()\n" +
              ".filter(operation -> \"${operationTypeClaim}\".equals(operation.operationType))\n" +
              ".map(operation -> operation.timestamp)\n" +
              ".map(dateFormatter::parse)\n" +
              ".sorted(Comparator.naturalOrder())\n" +
              ".collect(Collectors.toList());\n" +

            // Calculate idle time, which is the sum of differences between claim and unclaim timestamp pairs, ie (claim_n - unclaim_n)
            // Note there will always be at least one unclaim (startDate)
            "for(def i = 0; i < allUnclaimTimestamps.size() &&  i < allClaimTimestamps.size(); i++) {\n" +
              "def unclaimDate = allUnclaimTimestamps.get(i);\n" +
              "def claimDate= allClaimTimestamps.get(i);\n" +
              "def idleTimeToAdd = claimDate.getTime() - unclaimDate.getTime();\n" +
              "totalIdleTimeInMs = totalIdleTimeInMs + idleTimeToAdd;\n" +
              "idleTimeHasChanged = true;\n" +
            "}\n" +

            // Calculate work time, which is the sum of differences between unclaim and previous claim timestamp pairs, ie (unclaim_n+1 - claim_n)
            // Note the startDate is the first unclaim, so can be disregarded for this calculation
            "for(def i = 0; i < allUnclaimTimestamps.size() - 1 &&  i < allClaimTimestamps.size(); i++) {\n" +
              "def claimDate = allClaimTimestamps.get(i);\n" +
              "def unclaimDate = allUnclaimTimestamps.get(i + 1);\n" +
              "def workTimeToAdd = unclaimDate.getTime() - claimDate.getTime();\n" +
              "totalWorkTimeInMs = totalWorkTimeInMs + workTimeToAdd;\n" +
              "workTimeHasChanged = true;\n" +
            "}\n" +

            // Edge case: task was unclaimed and then completed without claim (== there are 2 more unclaims than claims)
            // --> add time between end and last "real" unclaim as idle time
            "if(allUnclaimTimestamps.size() - allClaimTimestamps.size() == 2) {\n" +
              "def lastUnclaim = allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 1);\n" +
              "def secondToLastUnclaim = allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 2);\n" +
              "totalIdleTimeInMs = totalIdleTimeInMs + (lastUnclaim.getTime() - secondToLastUnclaim.getTime());\n" +
              "idleTimeHasChanged = true;\n" +
            "}\n" +
          "}\n" +

        // Edge case: no assignee operations exist but task was finished (task was completed or canceled without claim)
        "else if(currentTask.${totalDurationInMsField} != null) {\n" +
          "def wasCanceled = Boolean.TRUE.equals(currentTask.${canceledField});\n" +
          "if(wasCanceled) {\n" +
            // Task was cancelled --> assumed to have been idle the entire time
            "totalIdleTimeInMs = currentTask.${totalDurationInMsField};\n" +
            "totalWorkTimeInMs = 0;\n" +
          "} else {\n" +
            // Task was not canceled --> assumed to have been worked on the entire time (presumably programmatically)
            "totalIdleTimeInMs = 0;\n" +
            "totalWorkTimeInMs = currentTask.${totalDurationInMsField};\n" +
          "}\n" +
          "workTimeHasChanged = true;\n" +
          "idleTimeHasChanged = true;\n" +
        "}\n" +

        // Set work and idle time if they have been calculated. Otherwise, leave fields null.
        "if(idleTimeHasChanged) {\n" +
          "currentTask.${idleDurationInMsField} = totalIdleTimeInMs;\n" +
        "}\n" +
        "if(workTimeHasChanged) {\n" +
          "currentTask.${workDurationInMsField} = totalWorkTimeInMs;\n" +
        "}\n" +
      "}\n" +
    "}\n"
    );
    // @formatter:on
  }

  protected UpdateRequest createUserTaskUpdateImportRequest(final Map.Entry<String, List<FlowNodeInstanceDto>> userTaskInstanceEntry) {
    final List<FlowNodeInstanceDto> userTasks = userTaskInstanceEntry.getValue();
    final String processInstanceId = userTaskInstanceEntry.getKey();

    final Script updateScript = createUpdateScript(userTasks);

    final FlowNodeInstanceDto firstUserTaskInstance = userTasks.stream().findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("No user tasks to import provided"));
    final ProcessInstanceDto procInst = ProcessInstanceDto.builder()
      .processInstanceId(processInstanceId)
      .engine(firstUserTaskInstance.getEngine())
      .flowNodeInstances(userTasks)
      .build();
    String newEntryIfAbsent;
    try {
      newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
    } catch (JsonProcessingException e) {
      String reason = String.format(
        "Error while processing JSON for user tasks of process instance with ID [%s].",
        processInstanceId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return new UpdateRequest()
      .index(getProcessInstanceIndexAliasName(firstUserTaskInstance.getProcessDefinitionKey()))
      .id(processInstanceId)
      .script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
  }

  protected List<ImportRequestDto> generateUserTaskImports(final String importItemName,
                                                           final OptimizeElasticsearchClient esClient,
                                                           final List<FlowNodeInstanceDto> userTaskInstances) {
    log.debug("Writing [{}] {} to ES.", userTaskInstances.size(), importItemName);

    createInstanceIndicesIfMissing(userTaskInstances, FlowNodeInstanceDto::getProcessDefinitionKey);

    Map<String, List<FlowNodeInstanceDto>> userTaskToProcessInstance = new HashMap<>();
    for (FlowNodeInstanceDto userTask : userTaskInstances) {
      userTaskToProcessInstance.putIfAbsent(userTask.getProcessInstanceId(), new ArrayList<>());
      userTaskToProcessInstance.get(userTask.getProcessInstanceId()).add(userTask);
    }

    return userTaskToProcessInstance.entrySet().stream()
      .map(entry -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(createUserTaskUpdateImportRequest(entry))
        .build())
      .collect(Collectors.toList());
  }

  private Script createUpdateScript(List<FlowNodeInstanceDto> userTasks) {
    final ImmutableMap<String, Object> scriptParameters = ImmutableMap.of(FLOW_NODE_INSTANCES, userTasks);
    return createDefaultScriptWithSpecificDtoParams(createInlineUpdateScript(), scriptParameters, objectMapper);
  }

}
