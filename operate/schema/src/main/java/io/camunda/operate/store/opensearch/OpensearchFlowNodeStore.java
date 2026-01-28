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

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStore implements FlowNodeStore {

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

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
}
