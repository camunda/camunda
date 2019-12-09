/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessDefinitionWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public IdDto createEventProcessDefinition(final EventProcessDefinitionDto eventProcessDto) {
    final String id = eventProcessDto.getId();
    log.debug("Writing event based process definition [{}].", eventProcessDto.getId());
    try {
      final IndexRequest request = new IndexRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME)
        .id(id)
        .source(
          objectMapper.writeValueAsString(eventProcessDto),
          XContentType.JSON
        )
        .setRefreshPolicy(IMMEDIATE);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format(
        "There was a problem while writing the event process definition [%s].", id
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    return new IdDto(id);
  }

  public boolean deleteEventProcessDefinition(final String eventProcessId) {
    log.debug("Deleting event process definition with id [{}].", eventProcessId);
    final DeleteRequest request = new DeleteRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME)
      .id(eventProcessId)
      .setRefreshPolicy(IMMEDIATE);

    final DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format(
        "Could not delete event process definition with id [%s].", eventProcessId
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    return deleteResponse.getResult().equals(DeleteResponse.Result.DELETED);
  }

}
