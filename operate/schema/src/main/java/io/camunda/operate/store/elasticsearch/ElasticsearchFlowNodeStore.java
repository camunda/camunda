/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.MAP_CLASS;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.*;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_ID;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.tenant.TenantCheckApplier;
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
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchFlowNodeStore implements FlowNodeStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private TenantCheckApplier<Query> es8TenantCheckApplier;

  @Autowired private ElasticsearchClient es8Client;

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
    final Query query =
        joinWithAnd(
            ElasticsearchUtil.termsQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
            ElasticsearchUtil.termsQuery(ListViewTemplate.ID, flowNodeInstanceId));
    final var tenantAwareQuery = es8TenantCheckApplier.apply(query);
    final var request =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(listViewTemplate, queryType))
            .query(tenantAwareQuery)
            .source(s -> s.filter(f -> f.includes(ACTIVITY_ID)))
            .build();

    try {
      final var res = es8Client.search(request, MAP_CLASS);
      if (res.hits().total().value() != 1) {
        throw new OperateRuntimeException("Flow node instance is not found: " + flowNodeInstanceId);
      } else {
        return ElasticsearchUtil.getFieldFromResponseObject(res, ACTIVITY_ID);
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
    final var query =
        ElasticsearchUtil.termsQuery(FlowNodeInstanceTemplate.KEY, parentFlowNodeInstanceKey);
    final var tenantAwareQuery = es8TenantCheckApplier.apply(query);
    final var request =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(flowNodeInstanceTemplate, queryType))
            .query(tenantAwareQuery)
            .source(s -> s.filter(f -> f.includes(FlowNodeInstanceTemplate.TREE_PATH)))
            .build();
    try {
      final var res = es8Client.search(request, MAP_CLASS);
      if (res.hits().total().value() > 0) {
        return ElasticsearchUtil.getFieldFromResponseObject(
            res, FlowNodeInstanceTemplate.TREE_PATH);
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
