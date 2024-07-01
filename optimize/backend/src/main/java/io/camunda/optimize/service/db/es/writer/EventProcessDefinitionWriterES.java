/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.writer.EventProcessDefinitionWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class EventProcessDefinitionWriterES implements EventProcessDefinitionWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Override
  public void importEventProcessDefinitions(
      final List<EventProcessDefinitionDto> definitionOptimizeDtos) {
    log.debug("Writing [{}] event process definitions to elastic.", definitionOptimizeDtos.size());
    final String importItemName = "event process definition information";
    esClient.doImportBulkRequestWithList(
        importItemName,
        definitionOptimizeDtos,
        this::addImportProcessDefinitionToRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  @Override
  public void deleteEventProcessDefinitions(final Collection<String> definitionIds) {
    final String importItemName = "event process definition ids";
    esClient.doImportBulkRequestWithList(
        importItemName,
        definitionIds,
        this::addDeleteProcessDefinitionToRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private void addImportProcessDefinitionToRequest(
      final BulkRequest bulkRequest, final EventProcessDefinitionDto processDefinitionDto) {
    @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
    final UpdateRequest updateRequest =
        new UpdateRequest()
            .index(EVENT_PROCESS_DEFINITION_INDEX_NAME)
            .id(processDefinitionDto.getId())
            .doc(objectMapper.convertValue(processDefinitionDto, Map.class))
            .docAsUpsert(true)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }

  private void addDeleteProcessDefinitionToRequest(
      final BulkRequest bulkRequest, final String processDefinitionId) {
    final DeleteRequest deleteRequest =
        new DeleteRequest().index(EVENT_PROCESS_DEFINITION_INDEX_NAME).id(processDefinitionId);

    bulkRequest.add(deleteRequest);
  }
}
