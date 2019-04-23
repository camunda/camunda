/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

public class ProcessInstanceQueryUtil {
  private static final Logger logger = LoggerFactory.getLogger(ProcessInstanceQueryUtil.class);

  public static Optional<OffsetDateTime> getLatestStartDate(final QueryBuilder baseQuery,
                                                            final RestHighLevelClient esClient) {
    final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .sort(SortBuilders.fieldSort(START_DATE).order(SortOrder.DESC))
      .size(1);
    final SearchRequest searchRequest = new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      return Arrays.stream(response.getHits().getHits())
        .findFirst()
        .map(documentFields -> (String) documentFields.getSourceAsMap().get(START_DATE))
        .map(dateTimeFormatter::parse)
        .map(OffsetDateTime::from);
    } catch (IOException e) {
      logger.warn("Could retrieve startDate of latest processInstance!");
    }

    return Optional.empty();
  }

}
