/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessDefinitionWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public void importEventProcessDefinitions(final List<EventProcessDefinitionDto> definitionOptimizeDtos) {
    log.debug("Writing [{}] event process definitions to elastic.", definitionOptimizeDtos.size());
    final String importItemName = "event process definition information";
    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      importItemName,
      definitionOptimizeDtos,
      this::addImportProcessDefinitionToRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  public void deleteEventProcessDefinitions(final Collection<String> definitionIds) {
    final String importItemName = "event process definition ids";
    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      importItemName,
      definitionIds,
      this::addDeleteProcessDefinitionToRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  private void addImportProcessDefinitionToRequest(final BulkRequest bulkRequest,
                                                   final EventProcessDefinitionDto processDefinitionDto) {
    @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
    final UpdateRequest updateRequest = new UpdateRequest()
      .index(EVENT_PROCESS_DEFINITION_INDEX_NAME)
      .id(processDefinitionDto.getId())
      .doc(objectMapper.convertValue(processDefinitionDto, Map.class))
      .docAsUpsert(true)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }

  private void addDeleteProcessDefinitionToRequest(final BulkRequest bulkRequest,
                                                   final String processDefinitionId) {
    final DeleteRequest deleteRequest = new DeleteRequest()
      .index(EVENT_PROCESS_DEFINITION_INDEX_NAME)
      .id(processDefinitionId);

    bulkRequest.add(deleteRequest);
  }

}
