/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStore implements FlowNodeStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private OperateProperties operateProperties;

  @Override
  public String getFlowNodeIdByFlowNodeInstanceId(final String flowNodeInstanceId) {
    record Result(String activityId) {}
    final RequestDSL.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? RequestDSL.QueryType.ALL
            : RequestDSL.QueryType.ONLY_RUNTIME;
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate, queryType)
            .query(
                withTenantCheck(
                    and(
                        term(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
                        term(ListViewTemplate.ID, flowNodeInstanceId))));

    return richOpenSearchClient
        .doc()
        .searchUnique(searchRequestBuilder, Result.class, flowNodeInstanceId)
        .activityId();
  }

  @Override
  public Map<String, String> getFlowNodeIdsForFlowNodeInstances(
      final Set<String> flowNodeInstanceIds) {
    record Result(String flowNodeId) {}
    final Map<String, String> flowNodeIdsMap = new HashMap<>();
    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate, RequestDSL.QueryType.ONLY_RUNTIME)
            .query(withTenantCheck(stringTerms(FlowNodeInstanceTemplate.ID, flowNodeInstanceIds)));
    final Consumer<List<Hit<Result>>> hitsConsumer =
        hits -> hits.forEach(h -> flowNodeIdsMap.put(h.id(), h.source().flowNodeId()));

    richOpenSearchClient.doc().scrollWith(searchRequestBuilder, Result.class, hitsConsumer);

    return flowNodeIdsMap;
  }

  @Override
  public String findParentTreePathFor(final long parentFlowNodeInstanceKey) {
    return findParentTreePath(parentFlowNodeInstanceKey, 0);
  }

  private String findParentTreePath(final long parentFlowNodeInstanceKey, final int attemptCount) {
    record Result(String treePath) {}
    final RequestDSL.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? RequestDSL.QueryType.ALL
            : RequestDSL.QueryType.ONLY_RUNTIME;
    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate, queryType)
            .query(withTenantCheck(term(FlowNodeInstanceTemplate.KEY, parentFlowNodeInstanceKey)));

    final List<Hit<Result>> hits =
        richOpenSearchClient.doc().search(searchRequestBuilder, Result.class).hits().hits();

    if (hits.size() > 0) {
      return hits.get(0).source().treePath();
    } else if (attemptCount < 1 && operateProperties.getImporter().isRetryReadingParents()) {
      // retry for the case, when ELS has not yet refreshed the indices
      ThreadUtil.sleepFor(2000L);
      return findParentTreePath(parentFlowNodeInstanceKey, attemptCount + 1);
    } else {
      return null;
    }
  }
}
