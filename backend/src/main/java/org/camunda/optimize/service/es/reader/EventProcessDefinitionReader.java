/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessMappingDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessDefinitionReader {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public Optional<EventProcessDefinitionDto> getEventProcessDefinitionByKeyOmitXml(final String eventProcessDefinitionKey) {
    log.debug("Fetching event based process definition with key [{}].", eventProcessDefinitionKey);
    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(DEFINITION_KEY, eventProcessDefinitionKey));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(1)
      .fetchSource(null, PROCESS_DEFINITION_XML);

    SearchRequest searchRequest = new SearchRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason = String.format(
        "Could not fetch event based process definition with key [%s].", eventProcessDefinitionKey
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getHits().length == 0) {
      return Optional.empty();
    }

    SearchHit hit = searchResponse.getHits().getAt(0);
    final String sourceAsString = hit.getSourceAsString();
    try {
      final EventProcessDefinitionDto definitionDto = objectMapper.readValue(
        sourceAsString,
        EventProcessDefinitionDto.class
      );
      return Optional.of(definitionDto);
    } catch (JsonProcessingException e) {
      final String reason = "It was not possible to deserialize a hit from Elasticsearch!"
        + " Hit response from Elasticsearch: " + sourceAsString;
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason);
    }
  }

  public List<EventProcessDefinitionDto> getAllEventProcessDefinitionsOmitXml() {
    log.debug("Fetching all available event based processes definitions.");
    String[] fieldsToExclude = new String[]{EsEventProcessMappingDto.Fields.xml};
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest = new SearchRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve event based processes!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      scrollResp,
      EventProcessDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    );
  }
}
