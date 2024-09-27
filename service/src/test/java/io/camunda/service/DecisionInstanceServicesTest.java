/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.clients.sort.SortOptionsBuilders.sort;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.query.filter.DecisionInstanceSearchQueryStub;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedCamundaSearchClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionInstanceServicesTest {

  private DecisionInstanceServices services;
  private StubbedCamundaSearchClient client;
  private DecisionInstanceSearchQueryStub decisionInstanceSearchQueryStub;

  @BeforeEach
  public void before() {
    client = spy(new StubbedCamundaSearchClient());
    decisionInstanceSearchQueryStub = new DecisionInstanceSearchQueryStub();
    decisionInstanceSearchQueryStub.registerWith(client);
    services = new DecisionInstanceServices(null, client);
  }

  @Test
  void shouldGetDecisionInstanceByKey() {
    // given
    final Long decisionInstanceKey = 1L;

    // when
    services.getByKey(decisionInstanceKey);

    // then
    verify(client)
        .search(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-decision-instance-8.3.0_alias")
                        .query(q -> q.term(t -> t.field("key").value(decisionInstanceKey)))
                        .sort(s -> s.field(f -> f.field("key").asc()))),
            DecisionInstanceEntity.class);
  }

  @Test
  void shouldSearchDecisionInstances() {
    // given
    final DecisionInstanceQuery query =
        SearchQueryBuilders.decisionInstanceSearchQuery(
            q ->
                q.filter(f -> f.tenantIds("tenant1"))
                    .sort(s -> s.evaluationDate().asc())
                    .page(p -> p.size(20)));

    // when
    services.search(query);

    // then
    verify(client)
        .search(
            SearchQueryRequest.of(
                b ->
                    b.index("operate-decision-instance-8.3.0_alias")
                        .query(q -> q.term(t -> t.field("tenantId").value("tenant1")))
                        .sort(
                            sort(
                                s ->
                                    s.field(f -> f.field("evaluationDate").asc().missing("_last"))),
                            sort(s -> s.field(f -> f.field("key").asc())))
                        .size(20)
                        .source(
                            s ->
                                s.filter(
                                    f ->
                                        f.excludes(
                                            List.of("evaluatedInputs", "evaluatedOutputs"))))),
            DecisionInstanceEntity.class);
  }
}
