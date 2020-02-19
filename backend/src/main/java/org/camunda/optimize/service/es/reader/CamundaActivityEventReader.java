/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaActivityEventReader {
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter formatter;

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAfter(final String definitionKey,
                                                                                  final Long eventTimestamp,
                                                                                  final int limit) {
    log.debug(
      "Fetching camunda activity events for key [{}] and with timestamp after {}", definitionKey, eventTimestamp
    );

    final RangeQueryBuilder timestampQuery = rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
      .gt(formatter.format(convertToOffsetDateTime(eventTimestamp)));

    return getPageOfEventsForDefinitionKeySortedByTimestamp(definitionKey, timestampQuery, limit);
  }

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAt(final String definitionKey,
                                                                               final Long eventTimestamp) {
    log.debug(
      "Fetching camunda activity events for key [{}] and with exact timestamp {}.", definitionKey, eventTimestamp
    );

    final RangeQueryBuilder timestampQuery = rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
      .lte(formatter.format(convertToOffsetDateTime(eventTimestamp)))
      .gte(formatter.format(convertToOffsetDateTime(eventTimestamp)));

    return getPageOfEventsForDefinitionKeySortedByTimestamp(definitionKey, timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  private OffsetDateTime convertToOffsetDateTime(final Long eventTimestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(eventTimestamp), ZoneId.systemDefault());
  }

  private List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(final String definitionKey,
                                                                                         final QueryBuilder query,
                                                                                         final int limit) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(SortBuilders.fieldSort(CamundaActivityEventIndex.TIMESTAMP).order(ASC))
      .size(limit);

    final SearchRequest searchRequest =
      new SearchRequest(new CamundaActivityEventIndex(definitionKey).getIndexName())
      .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return ElasticsearchHelper.mapHits(searchResponse.getHits(), CamundaActivityEventDto.class, objectMapper);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve camunda activity events!", e);
    }
  }
}
