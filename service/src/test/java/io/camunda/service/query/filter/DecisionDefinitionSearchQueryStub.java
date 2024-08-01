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
import io.camunda.service.entities.DecisionDefinitionEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class DecisionDefinitionSearchQueryStub implements RequestStub<DecisionDefinitionEntity> {

  @Override
  public SearchQueryResponse<DecisionDefinitionEntity> handle(final SearchQueryRequest request)
      throws Exception {

    final var decisionDefinition =
        new DecisionDefinitionEntity("t", 123L, "id", "dId", "name", 1, "drId", 2L, "drName", 3);

    final SearchQueryHit<DecisionDefinitionEntity> hit =
        new SearchQueryHit.Builder<DecisionDefinitionEntity>()
            .id("1234")
            .source(decisionDefinition)
            .build();

    final SearchQueryResponse<DecisionDefinitionEntity> response =
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
