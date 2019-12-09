/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventProcessMappingDto;
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

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessDefinitionReader {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public Optional<EventProcessDefinitionDto> getEventProcessDefinition(final String eventProcessDefinitionId) {
    log.debug("Fetching event based process definition with id [{}].", eventProcessDefinitionId);
    final GetRequest getRequest = new GetRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME).id(
      eventProcessDefinitionId);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String reason = String.format(
        "Could not fetch event based process definition with id [%s].", eventProcessDefinitionId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    EventProcessDefinitionDto result = null;
    if (getResponse.isExists()) {
      try {
        result = objectMapper.readValue(getResponse.getSourceAsString(), EventProcessDefinitionDto.class);
      } catch (IOException e) {
        final String reason = "Could not deserialize information for event based process definition with id: "
          + eventProcessDefinitionId;
        log.error(
          "Was not able to retrieve event based process  definition with id [{}]. Reason: {}",
          eventProcessDefinitionId,
          reason
        );
        throw new OptimizeRuntimeException(reason, e);
      }
    }

    return Optional.ofNullable(result);
  }

  public List<EventProcessDefinitionDto> getAllEventProcessDefinitionsOmitXml() {
    log.debug("Fetching all available event based processes definitions.");
    String[] fieldsToExclude = new String[]{IndexableEventProcessMappingDto.Fields.xml};
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest = new SearchRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve event based processes!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      EventProcessDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

}
