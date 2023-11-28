/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer.usertask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.writer.usertask.RunningUserTaskInstanceWriter;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;
import static org.camunda.optimize.service.es.writer.usertask.UserTaskDurationScriptUtil.createUpdateUserTaskMetricsScript;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class RunningUserTaskInstanceWriterOS extends AbstractUserTaskWriterOS implements RunningUserTaskInstanceWriter {

  @Autowired
  public RunningUserTaskInstanceWriterOS(final OptimizeOpenSearchClient osClient,
                                         final OpenSearchSchemaManager openSearchSchemaManager,
                                         final ObjectMapper objectMapper) {
    super(osClient, openSearchSchemaManager, objectMapper);
  }

  @Override
  public List<ImportRequestDto> generateUserTaskImports(final List<FlowNodeInstanceDto> userTaskInstances) {
    return super.generateUserTaskImports("running user task instances", osClient, userTaskInstances);
  }

  @Override
  protected String createInlineUpdateScript() {
    //todo will be handled in the OPT-7376
    return "";
  }

}
