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
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class ProcessInstanceSearchQueryStub implements RequestStub<ProcessInstanceEntity> {

  @Override
  public SearchQueryResponse<ProcessInstanceEntity> handle(final SearchQueryRequest request)
      throws Exception {

    final var processInstance =
        new ProcessInstanceEntity(
            123L,
            "Demo Process",
            5,
            "demoProcess",
            555L,
            789L,
            "2024-01-01T00:00:00Z",
            null,
            "ACTIVE",
            false,
            false,
            777L,
            "default",
            null,
            null,
            null);

    final SearchQueryHit<ProcessInstanceEntity> hit =
        new SearchQueryHit.Builder<ProcessInstanceEntity>()
            .id("1000")
            .source(processInstance)
            .build();

    final SearchQueryResponse<ProcessInstanceEntity> response =
        SearchQueryResponse.of(
            (f) -> {
              f.totalHits(1).hits(List.of(hit));
              return f;
            });

    return response;
  }

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this, ProcessInstanceEntity.class);
  }
}
