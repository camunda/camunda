/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.reader;

import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.camunda.operate.entities.FlowNodeInstanceEntity;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "camunda.operate.isNextFlowNodeInstances", havingValue = "true", matchIfMissing = false)
public class FlowNodeInstanceReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(FlowNodeInstanceReader.class);

  @Autowired(required = false)
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  public List<FlowNodeInstanceEntity> getFlowNodeInstances(FlowNodeInstanceRequestDto flowNodeInstanceRequest) {

    final TermQueryBuilder workflowInstanceKeyQuery = termQuery(FlowNodeInstanceTemplate.WORKFLOW_INSTANCE_KEY, flowNodeInstanceRequest.getWorkflowInstanceId());
    final String parentTreePath = flowNodeInstanceRequest.getParentTreePath();
    final TermQueryBuilder treePathQuery = termQuery(FlowNodeInstanceTemplate.TREE_PATH,
        parentTreePath);
    final TermQueryBuilder levelQuery = termQuery(FlowNodeInstanceTemplate.LEVEL, parentTreePath.split("/").length);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(constantScoreQuery(
            ElasticsearchUtil.joinWithAnd(workflowInstanceKeyQuery, treePathQuery, levelQuery)))
        .size(flowNodeInstanceRequest.getPageSize());

    applySorting(searchSourceBuilder, flowNodeInstanceRequest);

    final SearchRequest searchRequest = ElasticsearchUtil
        .createSearchRequest(flowNodeInstanceTemplate)
        .source(searchSourceBuilder);
    try {
      final SearchResponse searchResponse = esClient
          .search(searchRequest, RequestOptions.DEFAULT);
      List<FlowNodeInstanceEntity> flowNodeInstances = ElasticsearchUtil.mapSearchHits(searchResponse.getHits().getHits(),
          (sh) -> {
            FlowNodeInstanceEntity entity = ElasticsearchUtil.fromSearchHit(sh.getSourceAsString(), objectMapper, FlowNodeInstanceEntity.class);
            entity.setSortValues(sh.getSortValues());
            return entity;
          });

      if (flowNodeInstanceRequest.getSearchBefore() != null) {
        Collections.reverse(flowNodeInstances);
      }
      return flowNodeInstances;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all flow node instances: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }

  }

  private void applySorting(final SearchSourceBuilder searchSourceBuilder,
      final FlowNodeInstanceRequestDto request) {

    final boolean directSorting = request.getSearchAfter() != null || request.getSearchBefore() == null;

    if (directSorting) { //this sorting is also the default one for 1st page
      searchSourceBuilder
          .sort(FlowNodeInstanceTemplate.START_DATE, SortOrder.ASC)
          .sort(FlowNodeInstanceTemplate.ID, SortOrder.ASC);
      if (request.getSearchAfter() != null) {
        searchSourceBuilder.searchAfter(request.getSearchAfter());
      }
    } else { //searchBefore != null
      //reverse sorting
      searchSourceBuilder
          .sort(FlowNodeInstanceTemplate.START_DATE, SortOrder.DESC)
          .sort(FlowNodeInstanceTemplate.ID, SortOrder.DESC)
          .searchAfter(request.getSearchBefore());
    }

  }

}
