/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.reader.ListenerReader;
import io.camunda.operate.webapp.rest.dto.ListenerDto;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.ListenerResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.webapps.schema.descriptors.operate.template.JobTemplate;
import io.camunda.webapps.schema.entities.listener.ListenerType;
import io.camunda.webapps.schema.entities.operate.JobEntity;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.SearchResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchListenerReader extends OpensearchAbstractReader implements ListenerReader {

  private final JobTemplate jobTemplate;
  private final RichOpenSearchClient richOpenSearchClient;
  private final ObjectMapper objectMapper;

  private final Query executionListenersQ;
  private final Query taskListenersQ;

  public OpensearchListenerReader(
      final JobTemplate jobTemplate,
      final RichOpenSearchClient richOpenSearchClient,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.jobTemplate = jobTemplate;
    this.richOpenSearchClient = richOpenSearchClient;
    this.objectMapper = objectMapper;
    executionListenersQ = term(JobTemplate.JOB_KIND, ListenerType.EXECUTION_LISTENER.name());
    taskListenersQ = term(JobTemplate.JOB_KIND, ListenerType.TASK_LISTENER.name());
  }

  @Override
  public ListenerResponseDto getListenerExecutions(
      final String processInstanceId, final ListenerRequestDto request) {
    final Query query =
        and(
            term(JobTemplate.PROCESS_INSTANCE_KEY, processInstanceId),
            getFlowNodeQuery(request),
            getListenerTypeQuery(request));

    final var searchRequestBuilder = searchRequestBuilder(jobTemplate.getAlias()).query(query);
    applySorting(searchRequestBuilder, request);
    searchRequestBuilder.size(request.getPageSize());

    final SearchResult<JobEntity> searchResult =
        richOpenSearchClient.doc().fixedSearch(searchRequestBuilder.build(), JobEntity.class);
    final Long totalHitCount = searchResult.hits().total().value();
    final List<ListenerDto> listeners =
        searchResult.hits().hits().stream()
            .map(
                hit -> {
                  final JobEntity entity = hit.source();
                  final SortValuesWrapper[] sortValues =
                      SortValuesWrapper.createFrom(hit.sort().toArray(), objectMapper);
                  return ListenerDto.fromJobEntity(entity).setSortValues(sortValues);
                })
            .collect(Collectors.toList());
    if (request.getSearchBefore() != null) {
      Collections.reverse(listeners);
    }
    return new ListenerResponseDto(listeners, totalHitCount);
  }

  private Query getFlowNodeQuery(final ListenerRequestDto request) {
    if (request.getFlowNodeInstanceId() != null) {
      return term(JobTemplate.FLOW_NODE_INSTANCE_ID, request.getFlowNodeInstanceId());
    }
    return term(JobTemplate.FLOW_NODE_ID, request.getFlowNodeId());
  }

  private Query getListenerTypeQuery(final ListenerRequestDto request) {
    final ListenerType listenerFilter = request.getListenerTypeFilter();
    if (listenerFilter == null) {
      return or(executionListenersQ, taskListenersQ);
    } else if (listenerFilter.equals(ListenerType.EXECUTION_LISTENER)) {
      return executionListenersQ;
    } else if (listenerFilter.equals(ListenerType.TASK_LISTENER)) {
      return taskListenersQ;
    }
    throw new IllegalArgumentException("'listenerFilter' is set to an unsupported value.");
  }

  private void applySorting(
      final SearchRequest.Builder searchSourceBuilder, final ListenerRequestDto request) {

    SortOrder sortOrder = SortOrder.Desc;

    if (request.getSorting() != null) {
      if (request.getSorting().getSortOrder() != null) {
        sortOrder =
            "asc".equals(request.getSorting().getSortOrder()) ? SortOrder.Asc : SortOrder.Desc;
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
      searchSourceBuilder.searchAfter(CollectionUtil.toSafeListOfStrings(querySearchAfter));
    }

    searchSourceBuilder
        .sort(sortOptions(JobTemplate.TIME, sortOrder, missing))
        .sort(sortOptions(JobTemplate.JOB_KEY, sortOrder));
  }
}
