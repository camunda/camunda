/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.PageResultDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
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

  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(final String processDefinitionKey,
                                                                                               final OffsetDateTime endDate,
                                                                                               final Integer limit) {
    return getFirstPageOfProcessInstanceIdsForFilter(
      boolQuery()
        .filter(termQuery(ProcessInstanceIndex.PROCESS_DEFINITION_KEY, processDefinitionKey))
        .filter(rangeQuery(ProcessInstanceIndex.END_DATE).lt(dateTimeFormatter.format(endDate)))
        .filter(nestedQuery(VARIABLES, existsQuery(VARIABLES + "." + VARIABLE_ID), ScoreMode.None)),
      limit
    );
  }

  public PageResultDto<String> getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(final String processDefinitionKey,
                                                                                              final OffsetDateTime endDate,
                                                                                              final Integer limit,
                                                                                              final PageResultDto<String> previousPage) {
    return getNextPageOfProcessInstanceIds(
      previousPage,
      () -> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(processDefinitionKey, endDate, limit)
    );
  }

  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(final String processDefinitionKey,
                                                                               final OffsetDateTime endDate,
                                                                               final Integer limit) {
    return getFirstPageOfProcessInstanceIdsForFilter(
      boolQuery()
        .filter(termQuery(ProcessInstanceIndex.PROCESS_DEFINITION_KEY, processDefinitionKey))
        .filter(rangeQuery(ProcessInstanceIndex.END_DATE).lt(dateTimeFormatter.format(endDate))),
      limit
    );
  }

  public PageResultDto<String> getNextPageOfProcessInstanceIdsThatEndedBefore(final String processDefinitionKey,
                                                                              final OffsetDateTime endDate,
                                                                              final Integer limit,
                                                                              final PageResultDto<String> previousPage) {
    return getNextPageOfProcessInstanceIds(
      previousPage,
      () -> getFirstPageOfProcessInstanceIdsThatEndedBefore(processDefinitionKey, endDate, limit)
    );
  }

  private PageResultDto<String> getNextPageOfProcessInstanceIds(final PageResultDto<String> previousPage,
                                                                final Supplier<PageResultDto<String>> firstPageFetchFunction) {
    if (previousPage.isLastPage()) {
      return new PageResultDto<>(previousPage.getLimit());
    }
    try {
      return ElasticsearchReaderUtil.retrieveNextScrollResultsPage(
        previousPage.getPagingState(),
        String.class,
        searchHit -> (String) searchHit.getSourceAsMap().get(PROCESS_INSTANCE_ID),
        esClient,
        configurationService.getEsScrollTimeoutInSeconds(),
        previousPage.getLimit()
      );
    } catch (ElasticsearchStatusException e) {
      if (RestStatus.NOT_FOUND.equals(e.status())) {
        // this error occurs when the scroll id expired in the meantime, thus just restart it
        return firstPageFetchFunction.get();
      } else {
        throw e;
      }
    }
  }

  private PageResultDto<String> getFirstPageOfProcessInstanceIdsForFilter(final BoolQueryBuilder filterQuery,
                                                                          final Integer limit) {
    final PageResultDto<String> result = new PageResultDto<>(limit);
    final Integer resolvedLimit = Optional.ofNullable(limit).orElse(MAX_RESPONSE_SIZE_LIMIT);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(filterQuery)
      .fetchSource(PROCESS_INSTANCE_ID, null)
      // size of each scroll page, needs to be capped to max size of elasticsearch
      .size(resolvedLimit <= MAX_RESPONSE_SIZE_LIMIT ? resolvedLimit : MAX_RESPONSE_SIZE_LIMIT);

    final SearchRequest scrollSearchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getEsScrollTimeoutInSeconds()));

    try {
      final SearchResponse response = esClient.search(scrollSearchRequest, RequestOptions.DEFAULT);
      result.getEntities().addAll(ElasticsearchReaderUtil.mapHits(
        response.getHits(),
        resolvedLimit,
        String.class,
        searchHit -> (String) searchHit.getSourceAsMap().get(PROCESS_INSTANCE_ID)
      ));
      result.setPagingState(response.getScrollId());
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not obtain process instance ids.", e);
    }

    return result;
  }

}
