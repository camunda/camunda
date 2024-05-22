/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.activity;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TENANT_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.repository.script.ActivityInstanceScriptFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RunningActivityInstanceWriter extends AbstractActivityInstanceWriter {
  String UPDATE_USER_TASK_FIELDS_SCRIPT =
      Stream.of(
              FLOW_NODE_ID,
              USER_TASK_INSTANCE_ID,
              FLOW_NODE_INSTANCE_ID,
              FLOW_NODE_DEFINITION_KEY,
              FLOW_NODE_DEFINITION_VERSION,
              FLOW_NODE_TENANT_ID)
          .map(fieldKey -> String.format("existingTask.%s = newFlowNode.%s;%n", fieldKey, fieldKey))
          .collect(Collectors.joining());

  public RunningActivityInstanceWriter(
      final IndexRepository indexRepository, final ObjectMapper objectMapper) {
    super(objectMapper, indexRepository);
  }

  @Override
  protected String createInlineUpdateScript() {
    // already imported flowNodeInstances should win over the new flowNodeInstances, since the
    // stored instances are
    // probably completed instances.
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("flowNodesField", FLOW_NODE_INSTANCES)
                .put("flowNodeInstanceIdField", FLOW_NODE_INSTANCE_ID)
                .put("userTaskIdField", USER_TASK_INSTANCE_ID)
                .put("flowNodeTypeField", FLOW_NODE_TYPE)
                .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
                .build());

    return substitutor.replace(
        ActivityInstanceScriptFactory.createRunningActivityInlineUpdateScript(
            UPDATE_USER_TASK_FIELDS_SCRIPT));
  }
}
