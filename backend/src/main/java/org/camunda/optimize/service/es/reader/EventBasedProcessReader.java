/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventBasedProcessDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventBasedProcessDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_BASED_PROCESS_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@AllArgsConstructor
@Component
@Slf4j
public class EventBasedProcessReader {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public EventBasedProcessDto getEventBasedProcess(String eventBasedProcessId) {
    log.debug("Fetching event based process with id [{}]", eventBasedProcessId);
    GetRequest getRequest = new GetRequest(EVENT_BASED_PROCESS_INDEX_NAME).id(eventBasedProcessId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch event based process with id [%s]", eventBasedProcessId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      try {
        return objectMapper.readValue(getResponse.getSourceAsString(), IndexableEventBasedProcessDto.class)
          .toEventBasedProcessDto();
      } catch (IOException e) {
        String reason = "Could not deserialize information for event based process with ID: " + eventBasedProcessId;
        log.error(
          "Was not able to retrieve event based process with id [{}] from Elasticsearch. Reason: {}",
          eventBasedProcessId,
          reason
        );
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      log.error("Was not able to retrieve event based process with id [{}] from Elasticsearch.", eventBasedProcessId);
      throw new NotFoundException("Event based process does not exist! Tried to retrieve event based process with id "
                                    + eventBasedProcessId);
    }
  }

  public List<EventBasedProcessDto> getAllEventBasedProcessesOmitXml() {
    log.debug("Fetching all available event based processes");
    String[] fieldsToExclude = new String[]{IndexableEventBasedProcessDto.Fields.xml};
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest = new SearchRequest(EVENT_BASED_PROCESS_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve event based processes!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve event based processes!", e);
    }

    List<IndexableEventBasedProcessDto> indexableEventBasedProcessDtos =
      ElasticsearchHelper.retrieveAllScrollResults(scrollResp, IndexableEventBasedProcessDto.class,
                                                   objectMapper, esClient, configurationService.getElasticsearchScrollTimeout());
    return indexableEventBasedProcessDtos.stream()
      .map(IndexableEventBasedProcessDto::toEventBasedProcessDto)
      .collect(Collectors.toList());
  }

}
