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
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.ScrollException;
import io.camunda.operate.util.ElasticsearchTenantHelper;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchFlowNodeStore implements FlowNodeStore {

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private ElasticsearchTenantHelper tenantHelper;

  @Autowired private ElasticsearchClient esClient;

  @Override
  public Map<String, String> getFlowNodeIdsForFlowNodeInstances(
      final Set<String> flowNodeInstanceIds) {
    final var query =
        ElasticsearchUtil.termsQuery(FlowNodeInstanceTemplate.ID, flowNodeInstanceIds);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(whereToSearch(flowNodeInstanceTemplate, ONLY_RUNTIME))
            .query(tenantAwareQuery)
            .source(
                s ->
                    s.filter(
                        f ->
                            f.includes(
                                FlowNodeInstanceTemplate.ID,
                                FlowNodeInstanceTemplate.FLOW_NODE_ID)));

    try {
      final var resStream =
          ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, MAP_CLASS);

      return resStream
          .flatMap(res -> res.hits().hits().stream())
          .collect(
              Collectors.toMap(
                  hit -> hit.id(),
                  hit -> hit.source().get(FlowNodeInstanceTemplate.FLOW_NODE_ID).toString()));

    } catch (final ScrollException e) {
      throw new OperateRuntimeException(
          "Exception occurred when searching for flow node ids: " + e.getMessage(), e);
    }
  }
}
