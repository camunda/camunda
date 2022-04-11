/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex.INGESTION_TIMESTAMP;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

@RequiredArgsConstructor
@Component
@Slf4j
public class ExternalVariableReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAfter(final Long ingestTimestamp, final int limit) {
    log.debug("Fetching variables that where ingested after {}", Instant.ofEpochMilli(ingestTimestamp));

    final RangeQueryBuilder timestampQuery = rangeQuery(INGESTION_TIMESTAMP).gt(ingestTimestamp);

    return getPageOfVariablesSortedByIngestionTimestamp(timestampQuery, limit);
  }

  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAt(final Long ingestTimestamp) {
    log.debug("Fetching variables that where ingested at {}", Instant.ofEpochMilli(ingestTimestamp));

    final RangeQueryBuilder timestampQuery = rangeQuery(INGESTION_TIMESTAMP)
      .lte(ingestTimestamp)
      .gte(ingestTimestamp);

    return getPageOfVariablesSortedByIngestionTimestamp(timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  private List<ExternalProcessVariableDto> getPageOfVariablesSortedByIngestionTimestamp(final AbstractQueryBuilder<?> query,
                                                                                        final int limit) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(SortBuilders.fieldSort(INGESTION_TIMESTAMP).order(ASC))
      .size(limit);

    final SearchRequest searchRequest = new SearchRequest(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME)
      .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), ExternalProcessVariableDto.class, objectMapper);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve ingested variables by timestamp!", e);
    }
  }

}
