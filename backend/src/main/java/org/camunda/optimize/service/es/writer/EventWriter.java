/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Slf4j
@Component
public class EventWriter {
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void upsertEvent(final EventDto eventDto) {
    log.debug("Writing event [{}] to elasticsearch", eventDto.getId());
    try {
      final UpdateRequest request = createEventUpsert(eventDto);
      esClient.update(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = "There were errors while writing the event.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void upsertEvents(final List<EventDto> eventDtos) {
    log.debug("Writing [{}] events to elasticsearch", eventDtos.size());

    final BulkRequest bulkRequest = new BulkRequest();
    for (EventDto eventDto : eventDtos) {
      bulkRequest.add(createEventUpsert(eventDto));
    }

    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          final String errorMessage = String.format(
            "There were failures while writing events. Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        final String errorMessage = "There were errors while writing events.";
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
  }

  private UpdateRequest createEventUpsert(final EventDto eventDto) {
    return new UpdateRequest()
      .index(ElasticsearchConstants.EVENT_INDEX_NAME)
      .id(eventDto.getId())
      .doc(objectMapper.convertValue(eventDto, Map.class))
      .docAsUpsert(true);
  }
}
