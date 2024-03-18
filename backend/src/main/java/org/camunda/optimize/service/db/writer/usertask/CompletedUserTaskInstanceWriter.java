/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.usertask;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_DELETE_REASON;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_DUE_DATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.repository.script.UserTaskScriptFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CompletedUserTaskInstanceWriter extends AbstractUserTaskWriter {
  ImmutableSet<String> FIELDS_TO_UPDATE =
      ImmutableSet.of(
          FLOW_NODE_ID,
          USER_TASK_INSTANCE_ID,
          FLOW_NODE_INSTANCE_ID,
          FLOW_NODE_TOTAL_DURATION,
          FLOW_NODE_START_DATE,
          FLOW_NODE_END_DATE,
          USER_TASK_DUE_DATE,
          USER_TASK_DELETE_REASON);
  String UPDATE_USER_TASK_FIELDS_SCRIPT =
      FIELDS_TO_UPDATE.stream()
          .map(fieldKey -> String.format("existingTask.%s = newFlowNode.%s;%n", fieldKey, fieldKey))
          .collect(Collectors.joining());

  public CompletedUserTaskInstanceWriter(
      final IndexRepository indexRepository, final ObjectMapper objectMapper) {
    super(indexRepository, objectMapper);
  }

  public List<ImportRequestDto> generateUserTaskImports(
      final List<FlowNodeInstanceDto> userTaskInstances) {
    return super.generateUserTaskImports("completed user task instances", userTaskInstances);
  }

  @Override
  protected String createInlineUpdateScript() {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("flowNodesField", FLOW_NODE_INSTANCES)
                .put("userTaskIdField", USER_TASK_INSTANCE_ID)
                .put("flowNodeTypeField", FLOW_NODE_TYPE)
                .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
                .build());

    return substitutor.replace(
        UserTaskScriptFactory.createCompletedUserTaskInlineUpdateScript(
            UPDATE_USER_TASK_FIELDS_SCRIPT));
  }
}
