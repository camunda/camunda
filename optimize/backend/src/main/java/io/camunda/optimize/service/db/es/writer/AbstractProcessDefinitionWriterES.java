/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticSearchCondition.class)
public abstract class AbstractProcessDefinitionWriterES {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ObjectMapper objectMapper;
  protected final OptimizeElasticsearchClient esClient;

  public AbstractProcessDefinitionWriterES(
      final ObjectMapper objectMapper, final OptimizeElasticsearchClient esClient) {
    this.objectMapper = objectMapper;
    this.esClient = esClient;
  }

  abstract Script createUpdateScript(ProcessDefinitionOptimizeDto processDefinitionDtos);

  public void addImportProcessDefinitionToRequest(
      final BulkRequest bulkRequest, final ProcessDefinitionOptimizeDto processDefinitionDto) {
    final Script updateScript = createUpdateScript(processDefinitionDto);

    final UpdateRequest updateRequest =
        new UpdateRequest()
            .index(PROCESS_DEFINITION_INDEX_NAME)
            .id(processDefinitionDto.getId())
            .script(updateScript)
            .upsert(objectMapper.convertValue(processDefinitionDto, Map.class))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }
}
