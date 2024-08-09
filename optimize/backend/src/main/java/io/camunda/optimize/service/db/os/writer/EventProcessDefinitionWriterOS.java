/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import io.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.writer.EventProcessDefinitionWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class EventProcessDefinitionWriterOS implements EventProcessDefinitionWriter {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService indexNameService;

  @Override
  public void importEventProcessDefinitions(
      final List<EventProcessDefinitionDto> definitionOptimizeDtos) {
    log.debug(
        "Writing [{}] event process definitions to opensearch.", definitionOptimizeDtos.size());
    final String importItemName = "event process definition information";
    osClient.doImportBulkRequestWithList(
        importItemName,
        definitionOptimizeDtos,
        this::addImportProcessDefinitionToRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  @Override
  public void deleteEventProcessDefinitions(final Collection<String> definitionIds) {
    final String importItemName = "event process definition ids";
    osClient.doImportBulkRequestWithList(
        importItemName,
        definitionIds.stream().toList(),
        this::addDeleteProcessDefinitionToRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private BulkOperation addImportProcessDefinitionToRequest(
      final EventProcessDefinitionDto processDefinitionDto) {
    return new BulkOperation.Builder()
        .update(
            new UpdateOperation.Builder<EventProcessDefinitionDto>()
                .index(
                    indexNameService.getOptimizeIndexAliasForIndex(
                        EVENT_PROCESS_DEFINITION_INDEX_NAME))
                .id(processDefinitionDto.getId())
                .document(processDefinitionDto)
                .docAsUpsert(true)
                .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                .build())
        .build();
  }

  private BulkOperation addDeleteProcessDefinitionToRequest(final String processDefinitionId) {
    return new BulkOperation.Builder()
        .delete(
            new DeleteOperation.Builder()
                .index(
                    indexNameService.getOptimizeIndexAliasForIndex(
                        EVENT_PROCESS_DEFINITION_INDEX_NAME))
                .id(processDefinitionId)
                .build())
        .build();
  }
}
