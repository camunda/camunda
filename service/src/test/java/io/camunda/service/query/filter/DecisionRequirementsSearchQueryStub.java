/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class DecisionRequirementsSearchQueryStub
    implements RequestStub<DecisionRequirementsEntity> {

  @Override
  public SearchQueryResponse<DecisionRequirementsEntity> handle(final SearchQueryRequest request)
      throws Exception {

    final var decisionRequirement =
        new DecisionRequirementsEntity("t", 123L, "id", "dId", 1, "name");

    final SearchQueryHit<DecisionRequirementsEntity> hit =
        new SearchQueryHit.Builder<DecisionRequirementsEntity>()
            .id("1234")
            .source(decisionRequirement)
            .build();

    final SearchQueryResponse<DecisionRequirementsEntity> response =
        SearchQueryResponse.of(
            (f) -> {
              f.totalHits(1).hits(List.of(hit));
              return f;
            });

    return response;
  }

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this);
  }
}
