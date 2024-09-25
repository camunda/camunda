/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static java.util.Collections.emptyList;

import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.service.entities.FlowNodeInstanceEntity;
import io.camunda.service.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.service.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class FlowNodeInstanceSearchQueryStub implements RequestStub<FlowNodeInstanceEntity> {

  private final boolean returnEmptyResults = false;

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this, FlowNodeInstanceEntity.class);
  }

  @Override
  public SearchQueryResponse<FlowNodeInstanceEntity> handle(final SearchQueryRequest request)
      throws Exception {
    if (returnEmptyResults) {
      return SearchQueryResponse.of(f -> f.totalHits(0).hits(emptyList()));
    }

    final var flowNodeInstance =
        new FlowNodeInstanceEntity(
            1L,
            2L,
            3L,
            "startDate",
            "endDate",
            "flowNodeId",
            "treePath",
            FlowNodeType.SERVICE_TASK,
            FlowNodeState.ACTIVE,
            true,
            4L,
            5L,
            "complexProcessId",
            "tenantId");

    final SearchQueryHit<FlowNodeInstanceEntity> hit =
        new SearchQueryHit.Builder<FlowNodeInstanceEntity>()
            .id("1234")
            .source(flowNodeInstance)
            .build();

    final SearchQueryResponse<FlowNodeInstanceEntity> response =
        SearchQueryResponse.of(
            (f) -> {
              f.totalHits(1).hits(List.of(hit));
              return f;
            });

    return response;
  }
}
