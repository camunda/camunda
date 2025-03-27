/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.*;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.ListenerReader;
import io.camunda.operate.webapp.rest.dto.ListenerDto;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.ListenerResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.listener.ListenerType;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchListenerReader extends AbstractReader implements ListenerReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchListenerReader.class);

  private final JobTemplate jobTemplate;
  private final RestHighLevelClient esClient;

  private final TermQueryBuilder executionListenersQ;
  private final TermQueryBuilder taskListenersQ;

  public ElasticsearchListenerReader(
      final JobTemplate jobTemplate, final RestHighLevelClient esClient) {
    this.jobTemplate = jobTemplate;
    this.esClient = esClient;
    executionListenersQ = termQuery(JobTemplate.JOB_KIND, ListenerType.EXECUTION_LISTENER);
    taskListenersQ = termQuery(JobTemplate.JOB_KIND, ListenerType.TASK_LISTENER);
  }

  @Override
  public ListenerResponseDto getListenerExecutions(
      final String processInstanceId, final ListenerRequestDto request) {
    final TermQueryBuilder processInstanceQ =
        termQuery(JobTemplate.PROCESS_INSTANCE_KEY, processInstanceId);

    final SearchSourceBuilder sourceBuilder =
        new SearchSourceBuilder()
            .query(
                joinWithAnd(
                    processInstanceQ, getFlowNodeQuery(request), getListenerTypeQuery(request)))
            .size(request.getPageSize());

    applySorting(sourceBuilder, request);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(jobTemplate, ALL).source(sourceBuilder);

    final Long totalHitCount;
    final List<ListenerDto> listeners;
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      totalHitCount = response.getHits().getTotalHits().value;
      listeners =
          ElasticsearchUtil.mapSearchHits(
              response.getHits().getHits(),
              (searchHit) -> {
                final JobEntity entity =
                    fromSearchHit(searchHit.getSourceAsString(), objectMapper, JobEntity.class);
                final ListenerDto dto = ListenerDto.fromJobEntity(entity);
                final SortValuesWrapper[] sortValues =
                    SortValuesWrapper.createFrom(searchHit.getSortValues(), objectMapper);
                dto.setSortValues(sortValues);
                return dto;
              });
      if (request.getSearchBefore() != null) {
        Collections.reverse(listeners);
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred while searching for listeners: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
    return new ListenerResponseDto(listeners, totalHitCount);
  }

  private TermQueryBuilder getFlowNodeQuery(final ListenerRequestDto request) {
    if (request.getFlowNodeInstanceId() != null) {
      return termQuery(JobTemplate.FLOW_NODE_INSTANCE_ID, request.getFlowNodeInstanceId());
    }
    return termQuery(JobTemplate.FLOW_NODE_ID, request.getFlowNodeId());
  }

  private QueryBuilder getListenerTypeQuery(final ListenerRequestDto request) {
    final ListenerType listenerFilter = request.getListenerTypeFilter();
    if (listenerFilter == null) {
      return joinWithOr(executionListenersQ, taskListenersQ);
    } else if (listenerFilter.equals(ListenerType.EXECUTION_LISTENER)) {
      return executionListenersQ;
    } else if (listenerFilter.equals(ListenerType.TASK_LISTENER)) {
      return taskListenersQ;
    }
    throw new IllegalArgumentException("'listenerFilter' is set to an unsupported value.");
  }

  private void applySorting(
      final SearchSourceBuilder searchSourceBuilder, final ListenerRequestDto request) {

    SortOrder sortOrder = SortOrder.DESC; // default
    if (request.getSorting() != null) {
      if (request.getSorting().getSortOrder() != null) {
        sortOrder = SortOrder.fromString(request.getSorting().getSortOrder());
      }
    }

    final String missing;
    Object[] querySearchAfter = null;
    if (request.getSearchBefore() != null) {
      sortOrder = reverseOrder(sortOrder);
      missing = "_last";
      querySearchAfter = request.getSearchBefore(objectMapper);
    } else {
      missing = "_first";
      if (request.getSearchAfter() != null) {
        querySearchAfter = request.getSearchAfter(objectMapper);
      }
    }

    if (querySearchAfter != null) {
      searchSourceBuilder.searchAfter(querySearchAfter);
    }

    searchSourceBuilder
        .sort(SortBuilders.fieldSort(JobTemplate.TIME).order(sortOrder).missing(missing))
        .sort(SortBuilders.fieldSort(JobTemplate.JOB_KEY).order(sortOrder));
  }
}
