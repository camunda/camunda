/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;

@Conditional(OpenSearchCondition.class)
public class AbstractProcessInstanceWriterOS extends AbstractProcessInstanceDataWriterOS<ProcessInstanceDto>  {

  protected final ObjectMapper objectMapper;

  protected AbstractProcessInstanceWriterOS(final OptimizeOpenSearchClient osClient,
                                            final OpenSearchSchemaManager openSearchSchemaManager,
                                            final ObjectMapper objectMapper) {
    super(osClient, openSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

}
