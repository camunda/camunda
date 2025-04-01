/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.*;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_ID;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchFlowNodeStore implements FlowNodeStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private OperateProperties operateProperties;

  @Override
  public String getFlowNodeIdByFlowNodeInstanceId(final String flowNodeInstanceId) {
    // TODO Elasticsearch changes
    final ElasticsearchUtil.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? ElasticsearchUtil.QueryType.ALL
            : ElasticsearchUtil.QueryType.ONLY_RUNTIME;
    final QueryBuilder query =
        joinWithAnd(
            termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
            termQuery(ListViewTemplate.ID, flowNodeInstanceId));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType)
            .source(new SearchSourceBuilder().query(query).fetchSource(ACTIVITY_ID, null));
    final SearchResponse response;
    try {
      response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value != 1) {
        throw new OperateRuntimeException("Flow node instance is not found: " + flowNodeInstanceId);
      } else {
        return String.valueOf(response.getHits().getAt(0).getSourceAsMap().get(ACTIVITY_ID));
      }
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Error occurred when searching for flow node instance: " + flowNodeInstanceId, e);
    }
  }

  @Override
  public Map<String, String> getFlowNodeIdsForFlowNodeInstances(
      final Set<String> flowNodeInstanceIds) {
    final Map<String, String> flowNodeIdsMap = new HashMap<>();
    final QueryBuilder q = termsQuery(FlowNodeInstanceTemplate.ID, flowNodeInstanceIds);
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(q)
                    .fetchSource(
                        new String[] {
                          FlowNodeInstanceTemplate.ID, FlowNodeInstanceTemplate.FLOW_NODE_ID
                        },
                        null));
    try {
      tenantAwareClient.search(
          request,
          () -> {
            scrollWith(
                request,
                esClient,
                searchHits -> {
                  Arrays.stream(searchHits.getHits())
                      .forEach(
                          h ->
                              flowNodeIdsMap.put(
                                  h.getId(),
                                  (String)
                                      h.getSourceAsMap()
                                          .get(FlowNodeInstanceTemplate.FLOW_NODE_ID)));
                },
                null,
                null);
            return null;
          });
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred when searching for flow node ids: " + e.getMessage(), e);
    }
    return flowNodeIdsMap;
  }

  @Override
  public String findParentTreePathFor(final long parentFlowNodeInstanceKey) {
    return findParentTreePath(parentFlowNodeInstanceKey, 0);
  }

  private String findParentTreePath(final long parentFlowNodeInstanceKey, final int attemptCount) {
    final ElasticsearchUtil.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? ElasticsearchUtil.QueryType.ALL
            : ElasticsearchUtil.QueryType.ONLY_RUNTIME;
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate, queryType)
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(FlowNodeInstanceTemplate.KEY, parentFlowNodeInstanceKey))
                    .fetchSource(FlowNodeInstanceTemplate.TREE_PATH, null));
    try {
      final SearchHits hits = tenantAwareClient.search(searchRequest).getHits();
      if (hits.getTotalHits().value > 0) {
        return (String) hits.getHits()[0].getSourceAsMap().get(FlowNodeInstanceTemplate.TREE_PATH);
      } else if (attemptCount < 1 && operateProperties.getImporter().isRetryReadingParents()) {
        // retry for the case, when ELS has not yet refreshed the indices
        ThreadUtil.sleepFor(2000L);
        return findParentTreePath(parentFlowNodeInstanceKey, attemptCount + 1);
      } else {
        return null;
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for parent flow node instance processes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
