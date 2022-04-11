/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;
import static org.camunda.optimize.service.es.writer.usertask.UserTaskDurationScriptUtil.createUpdateUserTaskMetricsScript;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@Component
@Slf4j
public class CompletedActivityInstanceWriter extends AbstractActivityInstanceWriter {

  private static final Set<String> USER_TASK_FIELDS_TO_UPDATE = Set.of(
    FLOW_NODE_ID, USER_TASK_INSTANCE_ID, FLOW_NODE_INSTANCE_ID, FLOW_NODE_CANCELED,
    FLOW_NODE_DEFINITION_KEY, FLOW_NODE_DEFINITION_VERSION, FLOW_NODE_TENANT_ID
  );
  private static final String UPDATE_USER_TASK_FIELDS_SCRIPT = USER_TASK_FIELDS_TO_UPDATE
    .stream()
    .map(fieldKey -> String.format("existingTask.%s = newFlowNode.%s;%n", fieldKey, fieldKey))
    .collect(Collectors.joining());

  public CompletedActivityInstanceWriter(final OptimizeElasticsearchClient esClient,
                                         final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                         final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager, objectMapper);
  }

  protected String createInlineUpdateScript() {
    // new import flowNodeInstances should win over already imported flowNodeInstances, since those might be running
    // instances.
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("flowNodesField", FLOW_NODE_INSTANCES)
        .put("flowNodeInstanceIdField", FLOW_NODE_INSTANCE_ID)
        .put("userTaskIdField", USER_TASK_INSTANCE_ID)
        .put("flowNodeTypeField", FLOW_NODE_TYPE)
        .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
        .put("flowNodeCanceledField", FLOW_NODE_CANCELED)
        .put("flowNodeEndDateField", FLOW_NODE_END_DATE)
        .build()
    );

    // @formatter:off
    return substitutor.replace(
      "" +
      "def existingFlowNodeInstancesById = ctx._source.${flowNodesField}.stream()" +
      " .filter(flowNode -> !\"${userTaskFlowNodeType}\".equalsIgnoreCase(flowNode.${flowNodeTypeField}))" +
      " .collect(Collectors.toMap(n -> n.${flowNodeInstanceIdField}, n -> n, (n1, n2) -> n1));" +

      "def flowNodeInstancesToAddById = params.${flowNodesField}.stream()" +
      " .filter(flowNode -> !\"${userTaskFlowNodeType}\".equalsIgnoreCase(flowNode.${flowNodeTypeField}))" +
      " .collect(Collectors.toMap(n -> n.${flowNodeInstanceIdField}, n -> n, (n1, n2) -> n1));" +

      "existingFlowNodeInstancesById.putAll(flowNodeInstancesToAddById);" +

      // For userTask flownodes, we cannot rely on the flowNodeInstanceId as identifier because the identityLinkLog only
      // has the userTaskID. Due to our IdentityLinkLogImport, it is possible to have userTasks with only userTaskId and
      // no flownodeId/FlownodeInstanceId. Also note flownodes that aren't userTasks do not have userTaskIDs.
      "def existingUserTaskInstancesById = ctx._source.${flowNodesField}.stream()" +
      " .filter(flowNode -> \"${userTaskFlowNodeType}\".equalsIgnoreCase(flowNode.${flowNodeTypeField}))" +
      " .collect(Collectors.toMap(u -> u.${userTaskIdField}, u -> u, (u1, u2) -> u1));" +

      "for (def newFlowNode : params.${flowNodesField}) {\n" +
        // Ignore flowNodes that aren't userTasks
        "if(!\"${userTaskFlowNodeType}\".equalsIgnoreCase(newFlowNode.${flowNodeTypeField})){ continue; }\n"+

        "def existingTask = existingUserTaskInstancesById.get(newFlowNode.${userTaskIdField});\n" +
        "if (existingTask != null) {\n" +
          UPDATE_USER_TASK_FIELDS_SCRIPT +
        "} else {\n" +
          "existingUserTaskInstancesById.put(newFlowNode.${userTaskIdField}, newFlowNode);\n" +
        "}\n" +
      "}\n" +

      "ctx._source.${flowNodesField} = [];\n" +
      "ctx._source.${flowNodesField}.addAll(existingFlowNodeInstancesById.values());\n" +
      "ctx._source.${flowNodesField}.addAll(existingUserTaskInstancesById.values());\n" +

      "if (params.${flowNodesField}.stream().anyMatch(flowNode -> " +
          "\"${userTaskFlowNodeType}\".equalsIgnoreCase(flowNode.${flowNodeTypeField}) " +
          "&& flowNode.${flowNodeCanceledField} " +
          "&& !flowNode.${flowNodeEndDateField}.isEmpty())){\n" +
        // update userTask metrics only if a userTask has been canceled and already has an endDate (imported via
        // CompletedUserTaskImport)
        createUpdateUserTaskMetricsScript() +
      "}"
    );
    // @formatter:on
  }

}