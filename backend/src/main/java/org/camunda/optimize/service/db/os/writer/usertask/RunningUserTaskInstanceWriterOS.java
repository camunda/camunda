/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer.usertask;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.writer.usertask.RunningUserTaskInstanceWriter;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

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
