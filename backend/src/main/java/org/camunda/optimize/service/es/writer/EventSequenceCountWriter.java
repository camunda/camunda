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
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.EventSequenceCountIndex;
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

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class EventSequenceCountWriter {

  private static final String ID_FIELD_SEPARATOR = ":";
  private static final String ID_EVENT_SEPARATOR = "%";

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void createEventSequenceCount(final EventSequenceCountDto eventSequenceCountDto) {
    String id = generateIdForEventSequenceCountDto(eventSequenceCountDto);
    eventSequenceCountDto.setId(id);
    log.debug("Writing event sequence count entry [{}] to elasticsearch", id);
    IndexResponse indexResponse;
    try {
      IndexRequest request = new IndexRequest(EVENT_SEQUENCE_COUNT_INDEX_NAME)
        .id(id)
        .source(objectMapper.writeValueAsString(eventSequenceCountDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);
      indexResponse = esClient.index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format("There was a problem while writing the event sequence count [%s].", id);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
      final String errorMessage = String.format("Could not write event sequence count [%s] to Elasticsearch.", id);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void updateEventSequenceCount(final EventSequenceCountDto eventSequenceCountDto) {
    String id = eventSequenceCountDto.getId();
    log.debug("Updating event sequence count [{}] in elasticsearch", id);
    UpdateResponse updateResponse;
    try {
      Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
        Sets.newHashSet(EventSequenceCountIndex.COUNT), eventSequenceCountDto, objectMapper
      );
      final UpdateRequest request = new UpdateRequest()
        .index(EVENT_SEQUENCE_COUNT_INDEX_NAME)
        .id(id)
        .script(updateScript)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      updateResponse = esClient.update(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format("There was a problem updating the event sequence count [%s].", id);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update sequence count state with id [%s]. Event sequence count does not exist!", id);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }

    if (!updateResponse.getResult().equals(IndexResponse.Result.UPDATED)) {
      String errorMessage = String.format("Could not update event sequence count [%s] to Elasticsearch.", id);
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  private String generateIdForEventSequenceCountDto(EventSequenceCountDto eventSequenceCountDto) {
    return new StringBuilder()
      .append(eventSequenceCountDto.getSourceEvent().getGroup()).append(ID_FIELD_SEPARATOR)
      .append(eventSequenceCountDto.getSourceEvent().getSource()).append(ID_FIELD_SEPARATOR)
      .append(eventSequenceCountDto.getSourceEvent().getEventName())
      .append(ID_EVENT_SEPARATOR)
      .append(eventSequenceCountDto.getTargetEvent().getGroup()).append(ID_FIELD_SEPARATOR)
      .append(eventSequenceCountDto.getTargetEvent().getSource()).append(ID_FIELD_SEPARATOR)
      .append(eventSequenceCountDto.getTargetEvent().getEventName())
      .toString();
  }

}
