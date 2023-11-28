/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.writer.activity.RunningActivityInstanceWriter;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class RunningActivityInstanceWriterOS extends AbstractActivityInstanceWriterOS implements RunningActivityInstanceWriter {

  public RunningActivityInstanceWriterOS(final OptimizeOpenSearchClient osClient,
                                         final OpenSearchSchemaManager openSearchSchemaManager,
                                         final ObjectMapper objectMapper) {
    super(osClient, openSearchSchemaManager, objectMapper);
  }

  @Override
  protected String createInlineUpdateScript() {
    //todo will be handled in the OPT-7376
    return "";
  }

}
