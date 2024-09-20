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
import io.camunda.service.entities.ProcessDefinitionEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class ProcessDefinitionSearchQueryStub implements RequestStub<ProcessDefinitionEntity> {

  private final boolean returnEmptyResults = false;

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this, ProcessDefinitionEntity.class);
  }

  @Override
  public SearchQueryResponse<ProcessDefinitionEntity> handle(final SearchQueryRequest request)
      throws Exception {
    if (returnEmptyResults) {
      return SearchQueryResponse.of(f -> f.totalHits(0).hits(emptyList()));
    }

    final var processDefinition =
        new ProcessDefinitionEntity(1L, "CAFE", "black-coffee", "cafe.bpmn", 2L, "alpha");

    final SearchQueryHit<ProcessDefinitionEntity> hit =
        new SearchQueryHit.Builder<ProcessDefinitionEntity>()
            .id("12345")
            .source(processDefinition)
            .build();

    final SearchQueryResponse<ProcessDefinitionEntity> response =
        SearchQueryResponse.of(
            (f) -> {
              f.totalHits(1).hits(List.of(hit));
              return f;
            });

    return response;
  }
}
