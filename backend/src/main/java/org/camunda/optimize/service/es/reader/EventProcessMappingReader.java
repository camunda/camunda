/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessMappingDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessMappingReader {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public Optional<EventProcessMappingDto> getEventProcessMapping(final String eventProcessMappingId) {
    log.debug("Fetching event based process with id [{}].", eventProcessMappingId);
    final GetRequest getRequest = new GetRequest(EVENT_PROCESS_MAPPING_INDEX_NAME).id(eventProcessMappingId);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      final String reason = String.format("Could not fetch event based process with id [%s].", eventProcessMappingId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    EventProcessMappingDto result = null;
    if (getResponse.isExists()) {
      try {
        result = objectMapper.readValue(getResponse.getSourceAsString(), EsEventProcessMappingDto.class)
          .toEventProcessMappingDto();
      } catch (IOException e) {
        String reason = "Could not deserialize information for event based process with ID: " + eventProcessMappingId;
        log.error(
          "Was not able to retrieve event based process with id [{}] from Elasticsearch. Reason: {}",
          eventProcessMappingId,
          reason
        );
        throw new OptimizeRuntimeException(reason, e);
      }
    }

    return Optional.ofNullable(result);
  }

  public List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml() {
    log.debug("Fetching all available event based processes.");
    String[] fieldsToExclude = new String[]{EsEventProcessMappingDto.Fields.xml};
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest = new SearchRequest(EVENT_PROCESS_MAPPING_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to retrieve event based processes!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve event based processes!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      scrollResp,
      EsEventProcessMappingDto.class,
      objectMapper,
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    ).stream().map(EsEventProcessMappingDto::toEventProcessMappingDto).collect(Collectors.toList());
  }

  public List<EventProcessRoleRequestDto<IdentityDto>> getEventProcessRoles(final String eventProcessMappingId) {
    log.debug("Fetching event process roles for event process mapping id [{}].", eventProcessMappingId);
    final GetRequest getRequest = new GetRequest(EVENT_PROCESS_MAPPING_INDEX_NAME)
      .id(eventProcessMappingId)
      .fetchSourceContext(new FetchSourceContext(true, new String[]{EventProcessMappingIndex.ROLES}, null));

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      final String reason = String.format(
        "Could not fetch roles for event based process with id [%s].",
        eventProcessMappingId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    List<EventProcessRoleRequestDto<IdentityDto>> result = Collections.emptyList();
    if (getResponse.isExists()) {
      try {
        result = objectMapper.readValue(getResponse.getSourceAsString(), EsEventProcessMappingDto.class)
          .getRoles();
      } catch (IOException e) {
        final String reason =
          "Could not deserialize information for event based process with id: " + eventProcessMappingId;
        log.error(
          "Was not able to retrieve roles for event based process with id [{}] from Elasticsearch. Reason: {}",
          eventProcessMappingId,
          reason
        );
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

}
