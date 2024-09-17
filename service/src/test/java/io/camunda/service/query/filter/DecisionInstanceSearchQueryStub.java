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
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.service.entities.DecisionInstanceEntity.DecisionType;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class DecisionInstanceSearchQueryStub implements RequestStub<DecisionInstanceEntity> {

  public static final long KEY = 1L;
  private boolean returnEmptyResults;

  @Override
  public SearchQueryResponse<DecisionInstanceEntity> handle(final SearchQueryRequest request) {

    if (returnEmptyResults) {
      return SearchQueryResponse.of(f -> f.totalHits(0).hits(emptyList()));
    }

    final var decisionInstance =
        new DecisionInstanceEntity(
            KEY,
            DecisionInstanceState.EVALUATED,
            "10-09-2024",
            "ef",
            2L,
            3L,
            "bpi",
            4L,
            "ei",
            "5",
            "ddi",
            "dn",
            6,
            DecisionType.DECISION_TABLE,
            "result");

    final var hit =
        new SearchQueryHit.Builder<DecisionInstanceEntity>()
            .id("1234")
            .source(decisionInstance)
            .build();

    final SearchQueryResponse<DecisionInstanceEntity> response =
        SearchQueryResponse.of(
            (f) -> {
              f.totalHits(1).hits(List.of(hit));
              return f;
            });

    return response;
  }

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this, DecisionInstanceEntity.class);
  }

  public void returnEmptyResults() {
    returnEmptyResults = true;
  }
}
