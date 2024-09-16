/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.sort.SearchSortOptions;
import io.camunda.search.clients.sort.SortOrder;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.service.query.filter.DecisionInstanceSearchQueryStub;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.sort.DecisionInstanceSort;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DecisionInstanceSortTest {
  private DecisionInstanceServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  void before() {
    client = new StubbedCamundaSearchClient();
    new DecisionInstanceSearchQueryStub().registerWith(client);
    services = new DecisionInstanceServices(null, client);
  }

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments("key", SortOrder.ASC, s -> s.key().asc()),
        new TestArguments("decisionId", SortOrder.ASC, s -> s.decisionKey().asc()),
        new TestArguments("decisionDefinitionId", SortOrder.DESC, s -> s.dmnDecisionId().desc()),
        new TestArguments("decisionName", SortOrder.DESC, s -> s.dmnDecisionName().desc()),
        new TestArguments("decisionType", SortOrder.DESC, s -> s.decisionType().desc()),
        new TestArguments("decisionVersion", SortOrder.DESC, s -> s.decisionVersion().desc()),
        new TestArguments("evaluationDate", SortOrder.DESC, s -> s.evaluationDate().desc()),
        new TestArguments("tenantId", SortOrder.ASC, s -> s.tenantId().asc()),
        new TestArguments(
            "processDefinitionKey", SortOrder.ASC, s -> s.processDefinitionKey().asc()),
        new TestArguments("processInstanceId", SortOrder.ASC, s -> s.processInstanceId().asc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  void shouldSortByField(
      final String field,
      final SortOrder sortOrder,
      final Function<DecisionInstanceSort.Builder, ObjectBuilder<DecisionInstanceSort>> fn) {
    // when
    services.search(SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.sort(fn)));

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    final var sort = searchRequest.sort();
    assertThat(sort).hasSize(2);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(field);
              assertThat(t.field().order()).isEqualTo(sortOrder);
            });
    assertThat(sort.get(1))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo("key");
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  private record TestArguments(
      String field,
      SortOrder sortOrder,
      Function<DecisionInstanceSort.Builder, ObjectBuilder<DecisionInstanceSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
