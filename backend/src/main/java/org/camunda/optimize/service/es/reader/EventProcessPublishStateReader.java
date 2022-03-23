/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessPublishStateDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessPublishStateReader {
  private final ConfigurationService configurationService;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Optional<EventProcessPublishStateDto> getEventProcessPublishStateByEventProcessId(
    final String eventProcessMappingId) {
    log.debug("Fetching event process publish state with eventProcessMappingId [{}].", eventProcessMappingId);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(
        boolQuery()
          .must(termQuery(EventProcessPublishStateIndex.PROCESS_MAPPING_ID, eventProcessMappingId))
          .must(termQuery(EventProcessPublishStateIndex.DELETED, false))
      )
      .sort(SortBuilders.fieldSort(EventProcessPublishStateIndex.PUBLISH_DATE_TIME).order(SortOrder.DESC))
      .size(1);
    final SearchRequest searchRequest = new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME)
      .source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason = String.format(
        "Could not fetch event process publish state with id [%s].",
        eventProcessMappingId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    EventProcessPublishStateDto result = null;
    if (searchResponse.getHits().getTotalHits().value > 0) {
      try {
        result = objectMapper.readValue(
          searchResponse.getHits().getAt(0).getSourceAsString(),
          EsEventProcessPublishStateDto.class
        ).toEventProcessPublishStateDto();
      } catch (IOException e) {
        String reason =
          "Could not deserialize information for event process publish state with id: " + eventProcessMappingId;
        log.error(
          "Was not able to retrieve event process publish state with id [{}]. Reason: {}",
          eventProcessMappingId,
          reason
        );
        throw new OptimizeRuntimeException(reason, e);
      }
    }

    return Optional.ofNullable(result);
  }

  public List<EventProcessPublishStateDto> getAllEventProcessPublishStates() {
    return getAllEventProcessPublishStatesWithDeletedState(false);
  }

  public List<EventProcessPublishStateDto> getAllEventProcessPublishStatesWithDeletedState(final boolean deleted) {
    log.debug("Fetching all available event process publish states with deleted state [{}].", deleted);
    final TermQueryBuilder query = termQuery(EventProcessPublishStateIndex.DELETED, deleted);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(LIST_FETCH_LIMIT);
    final SearchRequest searchRequest = new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve event process publish states!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      scrollResp,
      EsEventProcessPublishStateDto.class,
      objectMapper,
      esClient,
      configurationService.getEsScrollTimeoutInSeconds()
    ).stream().map(EsEventProcessPublishStateDto::toEventProcessPublishStateDto).collect(Collectors.toList());
  }
}
