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
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.service.query.filter.DecisionDefinitionSearchQueryStub;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.sort.DecisionDefinitionSort;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DecisionDefinitionSortTest {
  private DecisionDefinitionServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new DecisionDefinitionSearchQueryStub().registerWith(client);
    services = new DecisionDefinitionServices(null, client);
  }

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments("id", SortOrder.ASC, s -> s.id().asc()),
        new TestArguments("decisionId", SortOrder.ASC, s -> s.decisionId().asc()),
        new TestArguments("name", SortOrder.DESC, s -> s.name().desc()),
        new TestArguments(
            "decisionRequirementsId", SortOrder.DESC, s -> s.decisionRequirementsId().desc()),
        new TestArguments(
            "decisionRequirementsKey", SortOrder.DESC, s -> s.decisionRequirementsKey().desc()),
        new TestArguments("tenantId", SortOrder.ASC, s -> s.tenantId().asc()),
        new TestArguments("version", SortOrder.ASC, s -> s.version().asc()),
        new TestArguments(
            "decisionRequirementsName", SortOrder.ASC, s -> s.decisionRequirementsName().asc()),
        new TestArguments(
            "decisionRequirementsVersion",
            SortOrder.ASC,
            s -> s.decisionRequirementsVersion().asc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      String field,
      SortOrder sortOrder,
      Function<DecisionDefinitionSort.Builder, ObjectBuilder<DecisionDefinitionSort>> fn) {
    // when
    services.search(SearchQueryBuilders.decisionDefinitionSearchQuery(q -> q.sort(fn)));

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
      Function<DecisionDefinitionSort.Builder, ObjectBuilder<DecisionDefinitionSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
