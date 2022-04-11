/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer.usertask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_DUE_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;
import static org.camunda.optimize.service.es.writer.usertask.UserTaskDurationScriptUtil.createUpdateUserTaskMetricsScript;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@Component
@Slf4j
public class RunningUserTaskInstanceWriter extends AbstractUserTaskWriter {
  private static final ImmutableSet<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    FLOW_NODE_ID, USER_TASK_INSTANCE_ID, FLOW_NODE_INSTANCE_ID,
    FLOW_NODE_START_DATE, USER_TASK_DUE_DATE
  );
  private static final String UPDATE_USER_TASK_FIELDS_SCRIPT = FIELDS_TO_UPDATE
    .stream()
    .map(fieldKey -> String.format("existingTask.%s = newFlowNode.%s;%n", fieldKey, fieldKey))
    .collect(Collectors.joining());

  @Autowired
  public RunningUserTaskInstanceWriter(final OptimizeElasticsearchClient esClient,
                                       final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                       final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager, objectMapper);
  }

  public List<ImportRequestDto> generateUserTaskImports(final List<FlowNodeInstanceDto> userTaskInstances) {
    return super.generateUserTaskImports("running user task instances", esClient, userTaskInstances);
  }

  protected String createInlineUpdateScript() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("flowNodesField", FLOW_NODE_INSTANCES)
        .put("userTaskIdField", USER_TASK_INSTANCE_ID)
        .put("flowNodeTypeField", FLOW_NODE_TYPE)
        .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
        .build()
    );

    // @formatter:off
    return substitutor.replace(
      "if (ctx._source.${flowNodesField} == null) { ctx._source.${flowNodesField} = []; } \n" +
      "def userTaskInstancesById = ctx._source.${flowNodesField}.stream()" +
        ".filter(flowNode -> \"${userTaskFlowNodeType}\".equalsIgnoreCase(flowNode.${flowNodeTypeField}))" +
        ".collect(Collectors.toMap(flowNode -> flowNode.${userTaskIdField}, flowNode -> flowNode, (fn1, fn2) -> fn1));\n" +
      "for (def newFlowNode : params.${flowNodesField}) {\n" +
        // Ignore flowNodes that aren't userTasks
        "if(!\"${userTaskFlowNodeType}\".equalsIgnoreCase(newFlowNode.${flowNodeTypeField})){ continue; }\n"+

        "def existingTask  = userTaskInstancesById.get(newFlowNode.${userTaskIdField});\n" +
        "if (existingTask != null) {\n" +
          UPDATE_USER_TASK_FIELDS_SCRIPT +
        "} else {\n" +
          "userTaskInstancesById.put(newFlowNode.${userTaskIdField}, newFlowNode);\n" +
        "}\n" +
      "}\n" +
      "ctx._source.${flowNodesField}.removeIf(flowNode -> \"${userTaskFlowNodeType}\".equalsIgnoreCase(flowNode.${flowNodeTypeField}));\n" +
      "ctx._source.${flowNodesField}.addAll(userTaskInstancesById.values());\n") +
      createUpdateUserTaskMetricsScript();
    // @formatter:on
  }

}
