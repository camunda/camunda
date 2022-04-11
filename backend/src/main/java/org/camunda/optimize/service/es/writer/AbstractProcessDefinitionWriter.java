/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

@AllArgsConstructor
public abstract class AbstractProcessDefinitionWriter {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ObjectMapper objectMapper;
  protected final OptimizeElasticsearchClient esClient;

  abstract Script createUpdateScript(ProcessDefinitionOptimizeDto processDefinitionDtos);

  protected void addImportProcessDefinitionToRequest(final BulkRequest bulkRequest,
                                                     final OptimizeDto optimizeDto) {
    if (!(optimizeDto instanceof ProcessDefinitionOptimizeDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    ProcessDefinitionOptimizeDto processDefinitionDto = (ProcessDefinitionOptimizeDto) optimizeDto;

    final Script updateScript = createUpdateScript(processDefinitionDto);

    final UpdateRequest updateRequest = new UpdateRequest()
      .index(PROCESS_DEFINITION_INDEX_NAME)
      .id(processDefinitionDto.getId())
      .script(updateScript)
      .upsert(objectMapper.convertValue(processDefinitionDto, Map.class))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }
}
