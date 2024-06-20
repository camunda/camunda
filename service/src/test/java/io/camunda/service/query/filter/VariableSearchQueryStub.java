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
import io.camunda.service.entities.VariableEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class VariableSearchQueryStub implements RequestStub<VariableEntity> {

  @Override
  public SearchQueryResponse<VariableEntity> handle(final SearchQueryRequest request)
      throws Exception {

    final var variable =
        new VariableEntity(23424L, "bar", "123", "123", false, 2L, 2L, 1232L, "test", "<default>");

    final SearchQueryHit<VariableEntity> hit =
        new SearchQueryHit.Builder<VariableEntity>().id("1234").source(variable).build();

    final SearchQueryResponse<VariableEntity> response =
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
