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
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.EventTraceStateIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;
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

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class EventTraceStateWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void createEventTraceState(final EventTraceStateDto eventTraceStateDto) {
    log.debug("Writing event trace state [{}] to elasticsearch", eventTraceStateDto);
    IndexResponse indexResponse;
    try {
      IndexRequest request = new IndexRequest(EVENT_TRACE_STATE_INDEX_NAME)
        .id(eventTraceStateDto.getTraceId())
        .source(objectMapper.writeValueAsString(eventTraceStateDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);
      indexResponse = esClient.index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format("There was a problem while writing the event trace state [%s].", eventTraceStateDto);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
      final String errorMessage = String.format("Could not write event trace state [%s] to Elasticsearch.", eventTraceStateDto);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void updateEventTraceState(final EventTraceStateDto eventTraceStateDto) {
    log.debug("Updating event trace state [{}] in elasticsearch", eventTraceStateDto);
    UpdateResponse updateResponse;
    try {
      Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
        Sets.newHashSet(EventTraceStateIndex.EVENT_TRACE), eventTraceStateDto, objectMapper
      );
      final UpdateRequest request = new UpdateRequest()
        .index(EVENT_TRACE_STATE_INDEX_NAME)
        .id(eventTraceStateDto.getTraceId())
        .script(updateScript)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      updateResponse = esClient.update(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format("There was a problem updating the event trace state [%s].", eventTraceStateDto);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update event trace state with id [%s]. Event trace state does not exist!", eventTraceStateDto);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }

    if (!updateResponse.getResult().equals(IndexResponse.Result.UPDATED)) {
      String errorMessage = String.format("Could not update event trace state [%s] to Elasticsearch.", eventTraceStateDto);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

}
