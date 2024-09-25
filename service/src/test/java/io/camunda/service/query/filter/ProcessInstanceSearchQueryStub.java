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
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProcessInstanceSearchQueryStub implements RequestStub<ProcessInstanceEntity> {

  private static final long PROCESS_INSTANCE_KEY_NO_MATCH = 100L;
  private static final long PROCESS_INSTANCE_KEY_DUPLICATE_MATCH = 200L;

  @Override
  public SearchQueryResponse<ProcessInstanceEntity> handle(final SearchQueryRequest request)
      throws Exception {

    final var processInstanceKey = getProcessInstanceKey(request).orElse(null);
    final var processInstance =
        new ProcessInstanceEntity(
            123L,
            "demoProcess",
            "Demo Process",
            5,
            "v5",
            789L,
            345L,
            333L,
            777L,
            "PI_1/PI_2",
            "2024-01-01T00:00:00Z",
            null,
            ProcessInstanceEntity.ProcessInstanceState.ACTIVE,
            false,
            "tenant");

    final SearchQueryHit<ProcessInstanceEntity> hit =
        new SearchQueryHit.Builder<ProcessInstanceEntity>()
            .id("1000")
            .source(processInstance)
            .build();

    final List<SearchQueryHit<ProcessInstanceEntity>> hits = new ArrayList<>();
    if (!Objects.equals(processInstanceKey, PROCESS_INSTANCE_KEY_NO_MATCH)) {
      hits.add(hit);
      if (Objects.equals(processInstanceKey, PROCESS_INSTANCE_KEY_DUPLICATE_MATCH)) {
        hits.add(hit);
      }
    }

    final SearchQueryResponse<ProcessInstanceEntity> response =
        SearchQueryResponse.of(
            (f) -> {
              f.totalHits(hits.size()).hits(hits);
              return f;
            });

    return response;
  }

  private Optional<Long> getProcessInstanceKey(final SearchQueryRequest request) {
    SearchQueryOption queryOption = request.query().queryOption();
    if ((queryOption instanceof SearchBoolQuery boolQuery) && (boolQuery.must().size() > 1)) {
      queryOption = boolQuery.must().get(1).queryOption();
      if (queryOption instanceof SearchTermQuery termQuery) {
        if (Objects.equals(termQuery.field(), "key") && termQuery.value().isLong()) {
          return Optional.of(termQuery.value().longValue());
        }
      }
    }

    return Optional.empty();
  }

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this, ProcessInstanceEntity.class);
  }
}
