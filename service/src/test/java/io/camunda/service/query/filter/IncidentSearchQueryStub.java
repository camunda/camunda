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
import io.camunda.service.entities.IncidentEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class IncidentSearchQueryStub implements RequestStub<IncidentEntity> {

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this, IncidentEntity.class);
  }

  @Override
  public SearchQueryResponse<IncidentEntity> handle(final SearchQueryRequest request)
      throws Exception {
    final var incident =
        new IncidentEntity(
            1L,
            2L,
            3L,
            "type",
            "flowNodeId",
            "flowNodeInstanceId",
            "creationTime",
            "state",
            4L,
            "tenantId",
            true,
            null,
            null,
            null);

    final SearchQueryHit<IncidentEntity> hit =
        new SearchQueryHit.Builder<IncidentEntity>().id("1234").source(incident).build();

    return SearchQueryResponse.of(
        (f) -> {
          f.totalHits(1).hits(List.of(hit));
          return f;
        });
  }
}
