/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class EventTraceStateReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public EventTraceStateDto getEventTraceState(String traceId) {
    log.debug("Fetching event trace state with id [{}]", traceId);

    GetRequest getRequest = new GetRequest(EVENT_TRACE_STATE_INDEX_NAME).id(traceId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch event trace state with id [%s]", traceId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, EventTraceStateDto.class);
      } catch (IOException e) {
        log.error(String.format("There was a problem fetching event trace state with id [%s] from Elasticsearch.", traceId));
        throw new OptimizeRuntimeException(String.format("Can't fetch event trace state for ID: %s", traceId));
      }
    } else {
      log.error("Count not find event trace with id [{}] from Elasticsearch.", traceId);
      throw new NotFoundException(String.format("Event trace state %s does not exist!", traceId));
    }
  }

}
