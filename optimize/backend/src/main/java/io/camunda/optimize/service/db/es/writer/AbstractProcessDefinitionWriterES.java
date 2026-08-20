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

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateOperationBuilderES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticSearchCondition.class)
public abstract class AbstractProcessDefinitionWriterES {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ObjectMapper objectMapper;
  protected final OptimizeElasticsearchClient esClient;
  protected final TaskRepositoryES taskRepositoryES;

  public AbstractProcessDefinitionWriterES(
      final ObjectMapper objectMapper,
      final OptimizeElasticsearchClient esClient,
      final TaskRepositoryES taskRepositoryES) {
    this.objectMapper = objectMapper;
    this.esClient = esClient;
    this.taskRepositoryES = taskRepositoryES;
  }

  abstract Script createUpdateScript(ProcessDefinitionOptimizeDto processDefinitionDtos);

  public void addImportProcessDefinitionToRequest(
      final BulkRequest.Builder bulkRequestBuilder,
      final ProcessDefinitionOptimizeDto processDefinitionDto) {
    final Script updateScript = createUpdateScript(processDefinitionDto);
    bulkRequestBuilder.operations(
        b ->
            b.update(
                OptimizeUpdateOperationBuilderES.of(
                    u ->
                        u.optimizeIndex(esClient, PROCESS_DEFINITION_INDEX_NAME)
                            .id(processDefinitionDto.getId())
                            .action(a -> a.script(updateScript).upsert(processDefinitionDto))
                            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT))));
  }
}
