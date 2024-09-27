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
import io.camunda.service.entities.FormEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class FormSearchQueryStub implements RequestStub<FormEntity> {
  @Override
  public SearchQueryResponse<FormEntity> handle(final SearchQueryRequest request) throws Exception {

    final List<SearchQueryHit<FormEntity>> hits =
        List.of(
            new SearchQueryHit.Builder<FormEntity>()
                .id("1")
                .source(new FormEntity("1", "tenant", "bpm", "schema", 1L))
                .build());

    final SearchQueryResponse<FormEntity> response =
        SearchQueryResponse.of(
            (f) -> {
              f.totalHits(hits.size()).hits(hits);
              return f;
            });

    return response;
  }

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this, FormEntity.class);
  }
}
