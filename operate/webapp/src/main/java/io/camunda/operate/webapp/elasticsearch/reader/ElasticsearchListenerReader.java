/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.JobEntity;
import io.camunda.operate.entities.ListenerType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.JobTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.ListenerReader;
import io.camunda.operate.webapp.rest.dto.ListenerDto;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchListenerReader extends AbstractReader implements ListenerReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchListenerReader.class);

  @Autowired private JobTemplate jobTemplate;
  @Autowired private RestHighLevelClient esClient;

  @Override
  public List<ListenerDto> getListenerExecutions(
      final String processInstanceId, final ListenerRequestDto request) {
    final TermQueryBuilder processInstanceQ =
        termQuery(JobTemplate.PROCESS_INSTANCE_KEY, processInstanceId);
    final TermQueryBuilder flowNodeIdQ =
        termQuery(JobTemplate.FLOW_NODE_ID, request.getFlowNodeId());
    final TermQueryBuilder executionListenersQ =
        termQuery(JobTemplate.JOB_KIND, ListenerType.EXECUTION_LISTENER);
    final TermQueryBuilder taskListenersQ =
        termQuery(JobTemplate.JOB_KIND, ListenerType.TASK_LISTENER);
    final SortBuilder sorting =
        SortBuilders.fieldSort(request.getSorting().getSortBy())
            .order(SortOrder.fromString(request.getSorting().getSortOrder()));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(jobTemplate, ALL)
            .source(
                new SearchSourceBuilder()
                    .query(
                        boolQuery()
                            .must(processInstanceQ)
                            .must(flowNodeIdQ)
                            .should(executionListenersQ)
                            .should(taskListenersQ))
                    .sort(sorting)
                    .size(request.getPageSize()));

    final List<JobEntity> jobEntities;
    try {
      jobEntities =
          ElasticsearchUtil.mapSearchHits(
              esClient.search(searchRequest, RequestOptions.DEFAULT).getHits().getHits(),
              objectMapper,
              JobEntity.class);

    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for batch operation metadata.", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
    final List<ListenerDto> listeners =
        jobEntities.stream()
            .map(job -> ListenerDto.fromJobEntity(job))
            .collect(Collectors.toUnmodifiableList());
    return listeners;
  }
}
