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
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;
import java.util.Map;

public class UserTaskSearchQueryStub implements RequestStub<UserTaskEntity> {

  @Override
  public SearchQueryResponse<UserTaskEntity> handle(final SearchQueryRequest request)
      throws Exception {

    final var userTask =
        new UserTaskEntity(
            123L,
            "flowNode1",
            "process1",
            "2020-01-01T00:00:00Z",
            "2020-01-02T00:00:00Z",
            "assignee1",
            "CREATED",
            1L,
            2L,
            3L,
            4L,
            "tenant1",
            "2020-01-03T00:00:00Z",
            "2020-01-04T00:00:00Z",
            List.of("group1"),
            List.of("user1"),
            "externalFormReference1",
            1,
            Map.of());

    final SearchQueryHit<UserTaskEntity> hit =
        new SearchQueryHit.Builder<UserTaskEntity>().id("1234").source(userTask).build();

    final SearchQueryResponse<UserTaskEntity> response =
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
