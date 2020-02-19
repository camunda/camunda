/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Slf4j
public class EventTraceStateWriter {

  private final String indexKey;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void upsertEventTraceStates(final List<EventTraceStateDto> eventTraceStateDtos) {
    log.debug("Writing [{}] event trace states to elasticsearch", eventTraceStateDtos.size());

    final BulkRequest bulkRequest = new BulkRequest();
    for (EventTraceStateDto eventTraceStateDto : eventTraceStateDtos) {
      bulkRequest.add(createEventTraceStateUpsertRequest(eventTraceStateDto));
    }

    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          final String errorMessage = String.format(
            "There were failures while writing event trace states. Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        final String errorMessage = "There were errors while writing event trace states.";
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
  }

  private UpdateRequest createEventTraceStateUpsertRequest(final EventTraceStateDto eventTraceStateDto) {
    return new UpdateRequest()
      .index(getIndexName())
      .id(eventTraceStateDto.getTraceId())
      .doc(objectMapper.convertValue(eventTraceStateDto, Map.class))
      .docAsUpsert(true);
  }

  private String getIndexName() {
    return new EventTraceStateIndex(indexKey).getIndexName();
  }

}
