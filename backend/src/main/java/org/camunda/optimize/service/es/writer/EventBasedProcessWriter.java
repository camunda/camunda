/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventBasedProcessDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventBasedProcessDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_BASED_PROCESS_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class EventBasedProcessWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public IdDto createEventBasedProcess(final EventBasedProcessDto eventBasedProcessDto) {
    String id = IdGenerator.getNextId();
    eventBasedProcessDto.setId(id);
    log.debug("Writing event based process [{}] to elasticsearch", id);
    IndexResponse indexResponse;
    try {
      IndexRequest request = new IndexRequest(EVENT_BASED_PROCESS_INDEX_NAME)
        .id(id)
        .source(objectMapper.writeValueAsString(IndexableEventBasedProcessDto.fromEventBasedProcessDto(eventBasedProcessDto)), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);
      indexResponse = esClient.index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format("There was a problem while writing the event based process [%s].", id);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
      final String errorMessage = String.format("Could not write event based process [%s] to Elasticsearch.", id);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
    return new IdDto(id);
  }

  public void updateEventBasedProcess(final EventBasedProcessDto eventBasedProcessDto) {
    String id = eventBasedProcessDto.getId();
    log.debug("Updating event based process [{}] in elasticsearch", id);
    UpdateResponse updateResponse;
    try {
      Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
        Sets.newHashSet("name", "xml", "mappings"),
        IndexableEventBasedProcessDto.fromEventBasedProcessDto(eventBasedProcessDto),
        objectMapper
      );
      final UpdateRequest request = new UpdateRequest()
        .index(EVENT_BASED_PROCESS_INDEX_NAME)
        .id(id)
        .script(updateScript)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      updateResponse = esClient.update(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format("There was a problem updating the event based process [%s].", id);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update event based process with id [%s]. Event based process does not exist!", id);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }

    if (!updateResponse.getResult().equals(IndexResponse.Result.UPDATED)) {
      String errorMessage = String.format("Could not update event based process [%s] to Elasticsearch.", id);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteEventBasedProcess(final String eventBasedProcessId) {
    log.debug("Deleting event based process with id [{}]", eventBasedProcessId);
    DeleteRequest request = new DeleteRequest(EVENT_BASED_PROCESS_INDEX_NAME)
      .id(eventBasedProcessId)
      .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String errorMessage = String.format("Could not delete event based process with id [%s]. ", eventBasedProcessId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String errorMessage = String.format("Could not delete event based process with id [%s]. Event based process does not exist." +
                                       "Maybe it was already deleted by someone else?", eventBasedProcessId);
      log.error(errorMessage);
      throw new NotFoundException(errorMessage);
    }
  }

}
