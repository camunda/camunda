/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchListenerReader extends AbstractReader implements ListenerReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchListenerReader.class);

  private final JobTemplate jobTemplate;

  private final Query executionListenersQ;
  private final Query taskListenersQ;

  public ElasticsearchListenerReader(final JobTemplate jobTemplate) {
    this.jobTemplate = jobTemplate;
    executionListenersQ =
        ElasticsearchUtil.termsQuery(JobTemplate.JOB_KIND, ListenerType.EXECUTION_LISTENER);
    taskListenersQ = ElasticsearchUtil.termsQuery(JobTemplate.JOB_KIND, ListenerType.TASK_LISTENER);
  }

  @Override
  public ListenerResponseDto getListenerExecutions(
      final String processInstanceId, final ListenerRequestDto request) {
    final var processInstanceQ =
        ElasticsearchUtil.termsQuery(JobTemplate.PROCESS_INSTANCE_KEY, processInstanceId);

    final var query =
        ElasticsearchUtil.joinWithAnd(
            processInstanceQ, getFlowNodeQuery(request), getListenerTypeQuery(request));

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(ElasticsearchUtil.whereToSearch(jobTemplate, ALL))
            .query(query)
            .size(request.getPageSize());

    applySorting(searchRequestBuilder, request);

    final var searchRequest = searchRequestBuilder.build();

    final Long totalHitCount;
    final List<ListenerDto> listeners;
    try {
      final var response = es8client.search(searchRequest, JobEntity.class);
      totalHitCount = response.hits().total().value();
      listeners =
          response.hits().hits().stream()
              .map(
                  hit -> {
                    final var entity = hit.source();
                    final var dto = ListenerDto.fromJobEntity(entity);
                    final var sortValues =
                        SortValuesWrapper.createFrom(
                            hit.sort().stream().map(FieldValue::_get).toArray(), objectMapper);
                    dto.setSortValues(sortValues);
                    return dto;
                  })
              .toList();
      if (request.getSearchBefore() != null) {
        return new ListenerResponseDto(List.copyOf(listeners).reversed(), totalHitCount);
      }
    } catch (final IOException e) {
      final var message =
          String.format("Exception occurred while searching for listeners: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
    return new ListenerResponseDto(listeners, totalHitCount);
  }

  private Query getFlowNodeQuery(final ListenerRequestDto request) {
    if (request.getFlowNodeInstanceId() != null) {
      return ElasticsearchUtil.termsQuery(
          JobTemplate.FLOW_NODE_INSTANCE_ID, request.getFlowNodeInstanceId());
    }
    return ElasticsearchUtil.termsQuery(JobTemplate.FLOW_NODE_ID, request.getFlowNodeId());
  }

  private Query getListenerTypeQuery(final ListenerRequestDto request) {
    final var listenerFilter = request.getListenerTypeFilter();
    if (listenerFilter == null) {
      return ElasticsearchUtil.joinWithOr(executionListenersQ, taskListenersQ);
    } else if (listenerFilter.equals(ListenerType.EXECUTION_LISTENER)) {
      return executionListenersQ;
    } else if (listenerFilter.equals(ListenerType.TASK_LISTENER)) {
      return taskListenersQ;
    }
    throw new IllegalArgumentException("'listenerFilter' is set to an unsupported value.");
  }

  private void applySorting(
      final SearchRequest.Builder searchRequestBuilder, final ListenerRequestDto request) {

    var sortOrder = SortOrder.Desc; // default
    if (request.getSorting() != null && request.getSorting().getSortOrder() != null) {
      sortOrder = ElasticsearchUtil.toSortOrder(request.getSorting().getSortOrder());
    }

    final String missing;
    Object[] querySearchAfter = null;
    if (request.getSearchBefore() != null) {
      sortOrder = ElasticsearchUtil.reverseOrder(sortOrder);
      missing = "_last";
      querySearchAfter = request.getSearchBefore(objectMapper);
    } else {
      missing = "_first";
      if (request.getSearchAfter() != null) {
        querySearchAfter = request.getSearchAfter(objectMapper);
      }
    }

    if (querySearchAfter != null) {
      searchRequestBuilder.searchAfter(
          ElasticsearchUtil.searchAfterToFieldValues(querySearchAfter));
    }

    searchRequestBuilder
        .sort(ElasticsearchUtil.sortOrder(JobTemplate.TIME, sortOrder, missing))
        .sort(ElasticsearchUtil.sortOrder(JobTemplate.JOB_KEY, sortOrder));
  }
}
