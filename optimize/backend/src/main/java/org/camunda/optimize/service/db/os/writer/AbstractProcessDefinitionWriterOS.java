/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;

@AllArgsConstructor
@Conditional(OpenSearchCondition.class)
public abstract class AbstractProcessDefinitionWriterOS {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ObjectMapper objectMapper;
  protected final OptimizeOpenSearchClient osClient;

  abstract Script createUpdateScript(ProcessDefinitionOptimizeDto processDefinitionDtos);

  public BulkOperation addImportProcessDefinitionToRequest(
      final ProcessDefinitionOptimizeDto processDefinitionDto) {
    final Script updateScript = createUpdateScript(processDefinitionDto);

    final UpdateOperation<ProcessDefinitionOptimizeDto> request =
        new UpdateOperation.Builder<ProcessDefinitionOptimizeDto>()
            .id(processDefinitionDto.getId())
            .script(updateScript)
            .upsert(processDefinitionDto)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
            .build();

    return new BulkOperation.Builder().update(request).build();
  }
}
