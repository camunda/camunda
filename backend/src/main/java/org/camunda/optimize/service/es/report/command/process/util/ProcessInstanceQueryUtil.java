/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@Slf4j
public class ProcessInstanceQueryUtil {

  public static Optional<OffsetDateTime> getLatestDate(final QueryBuilder baseQuery,
                                                       final String dateField,
                                                       final OptimizeElasticsearchClient esClient) {
    final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .sort(SortBuilders.fieldSort(dateField).order(SortOrder.DESC))
      .size(1);
    final SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME).source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      return Arrays.stream(response.getHits().getHits())
        .findFirst()
        .map(documentFields -> (String) documentFields.getSourceAsMap().get(dateField))
        .map(dateTimeFormatter::parse)
        .map(OffsetDateTime::from);
    } catch (IOException e) {
      log.warn("Could retrieve startDate of latest processInstance!");
    }

    return Optional.empty();
  }

}
