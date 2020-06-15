/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
public class ProcessInstanceReader {
  private final ConfigurationService configurationService;
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter;

  public List<String> getProcessInstanceIdsThatHaveVariablesAndEndedBefore(final String processDefinitionKey,
                                                                           final OffsetDateTime endDate,
                                                                           final Integer limit) {
    return getProcessInstanceIdsThatEndedBefore(
      boolQuery().filter(nestedQuery(VARIABLES, existsQuery(VARIABLES + "." + VARIABLE_ID), ScoreMode.None)),
      processDefinitionKey,
      endDate,
      limit
    );
  }

  public List<String> getProcessInstanceIdsThatEndedBefore(final String processDefinitionKey,
                                                           final OffsetDateTime endDate,
                                                           final Integer limit) {
    return getProcessInstanceIdsThatEndedBefore(boolQuery(), processDefinitionKey, endDate, limit);
  }

  public List<String> getProcessInstanceIdsThatEndedBefore(final BoolQueryBuilder filterQuery,
                                                           final String processDefinitionKey,
                                                           final OffsetDateTime endDate,
                                                           final Integer limit) {
    return getProcessInstanceIdsForFilter(
      filterQuery
        .filter(termQuery(ProcessInstanceIndex.PROCESS_DEFINITION_KEY, processDefinitionKey))
        .filter(rangeQuery(ProcessInstanceIndex.END_DATE).lt(dateTimeFormatter.format(endDate))),
      limit
    );
  }

  private List<String> getProcessInstanceIdsForFilter(final BoolQueryBuilder filterQuery, final Integer limit) {
    final Integer resolvedLimit = Optional.ofNullable(limit).orElse(MAX_RESPONSE_SIZE_LIMIT);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(filterQuery)
      .fetchSource(PROCESS_INSTANCE_ID, null)
      // size of each scroll page, needs to be capped to max size of elasticsearch
      .size(resolvedLimit <= MAX_RESPONSE_SIZE_LIMIT ? resolvedLimit : MAX_RESPONSE_SIZE_LIMIT);

    final SearchRequest scrollSearchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    try {
      final SearchResponse response = esClient.search(scrollSearchRequest, RequestOptions.DEFAULT);
      return ElasticsearchReaderUtil.retrieveScrollResultsTillLimit(
        response,
        String.class,
        searchHit -> (String) searchHit.getSourceAsMap().get(PROCESS_INSTANCE_ID),
        esClient,
        configurationService.getElasticsearchScrollTimeout(),
        resolvedLimit
      );
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not obtain process instance ids.", e);
    }
  }

}
