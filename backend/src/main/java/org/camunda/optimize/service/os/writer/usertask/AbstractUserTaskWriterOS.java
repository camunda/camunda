/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer.usertask;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.usertask.AbstractUserTaskWriter;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.os.writer.AbstractProcessInstanceDataWriterOS;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Conditional(OpenSearchCondition.class)
public abstract class AbstractUserTaskWriterOS extends AbstractProcessInstanceDataWriterOS<FlowNodeInstanceDto>
  implements AbstractUserTaskWriter {

  protected final ObjectMapper objectMapper;

  protected AbstractUserTaskWriterOS(final OptimizeOpenSearchClient osClient,
                                     final OpenSearchSchemaManager openSearchSchemaManager,
                                     final ObjectMapper objectMapper) {
    super(osClient, openSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  protected abstract String createInlineUpdateScript();

  @Override
  public List<ImportRequestDto> generateUserTaskImports(final String importItemName,
                                                        final DatabaseClient databaseClient,
                                                        final List<FlowNodeInstanceDto> userTaskInstances) {
    //todo will be handled in the OPT-7376
    return Collections.emptyList();
  }

}
