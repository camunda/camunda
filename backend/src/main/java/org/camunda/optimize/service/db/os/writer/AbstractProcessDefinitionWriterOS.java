/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.writer.AbstractProcessDefinitionWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;

@AllArgsConstructor
@Conditional(OpenSearchCondition.class)
public abstract class AbstractProcessDefinitionWriterOS implements AbstractProcessDefinitionWriter<BulkRequest> {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ObjectMapper objectMapper;
  protected final OptimizeOpenSearchClient osClient;

  abstract Script createUpdateScript(ProcessDefinitionOptimizeDto processDefinitionDtos);

  @Override
  public void addImportProcessDefinitionToRequest(final BulkRequest bulkRequest,
                                                  final ProcessDefinitionOptimizeDto processDefinitionDto) {
    //todo will be handled in the OPT-7376
  }

}
