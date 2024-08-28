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
import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;
import java.util.Set;

public class AuthorizationSearchQueryStub implements RequestStub<AuthorizationEntity> {

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this);
  }

  @Override
  public SearchQueryResponse<AuthorizationEntity> handle(final SearchQueryRequest request)
      throws Exception {
    final List<SearchQueryHit<AuthorizationEntity>> hits =
        List.of(
            new SearchQueryHit.Builder<AuthorizationEntity>()
                .id("1")
                .source(
                    new AuthorizationEntity(
                        new AuthorizationEntity.Authorization(
                            "username1",
                            "user",
                            "bpmnProcessId:123",
                            "process-definition",
                            Set.of("CREATE"))))
                .build(),
            new SearchQueryHit.Builder<AuthorizationEntity>()
                .id("2")
                .source(
                    new AuthorizationEntity(
                        new AuthorizationEntity.Authorization(
                            "username1",
                            "user",
                            "bpmnProcessId:456",
                            "process-definition",
                            Set.of("READ"))))
                .build());

    final SearchQueryResponse<AuthorizationEntity> response =
        SearchQueryResponse.of(
            (f) -> {
              f.totalHits(hits.size()).hits(hits);
              return f;
            });

    return response;
  }
}
