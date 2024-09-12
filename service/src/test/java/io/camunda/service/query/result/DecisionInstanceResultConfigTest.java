/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.service.query.filter.DecisionInstanceSearchQueryStub;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionInstanceResultConfigTest {
  private DecisionInstanceServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  void before() {
    client = new StubbedCamundaSearchClient();
    new DecisionInstanceSearchQueryStub().registerWith(client);
    services = new DecisionInstanceServices(null, null, null);
  }

  @Test
  void shouldSourceConfigIncludeEvaluatedInputs() {
    // when
    services.search(
        SearchQueryBuilders.decisionInstanceSearchQuery(
            q -> q.resultConfig(r -> r.evaluatedInputs().include())));

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    final var source = searchRequest.source();
    assertThat(source.sourceFilter().includes()).containsExactly("evaluatedInputs");
    assertThat(source.sourceFilter().excludes()).isEmpty();
  }

  @Test
  void shouldSourceConfigIncludeEvaluatedOutputs() {
    // when
    services.search(
        SearchQueryBuilders.decisionInstanceSearchQuery(
            q -> q.resultConfig(r -> r.evaluatedOutputs().exclude())));

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    final var source = searchRequest.source();
    assertThat(source.sourceFilter().excludes()).containsExactly("evaluatedOutputs");
    assertThat(source.sourceFilter().includes()).isEmpty();
  }

  @Test
  void shouldSourceConfigExcludeEvaluatedInputsAndEvaluatedOutputs() {
    // when
    services.search(
        SearchQueryBuilders.decisionInstanceSearchQuery(
            q -> q.resultConfig(r -> r.evaluatedInputs().exclude().evaluatedOutputs().exclude())));

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    final var source = searchRequest.source();
    assertThat(source.sourceFilter().excludes())
        .containsExactly("evaluatedInputs", "evaluatedOutputs");
    assertThat(source.sourceFilter().includes()).isEmpty();
  }
}
