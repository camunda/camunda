/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.sort.SearchSortOptions;
import io.camunda.search.clients.sort.SortOrder;
import io.camunda.service.FlowNodeInstanceServices;
import io.camunda.service.query.filter.FlowNodeInstanceSearchQueryStub;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.sort.FlowNodeInstanceSort;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class FlowNodeInstanceSortTest {

  private FlowNodeInstanceServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new FlowNodeInstanceSearchQueryStub().registerWith(client);
    services = new FlowNodeInstanceServices(null, client, null, null);
  }

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new FlowNodeInstanceSortTest.TestArguments(
            "key", SortOrder.ASC, s -> s.flowNodeInstanceKey().asc()),
        new FlowNodeInstanceSortTest.TestArguments(
            "processInstanceKey", SortOrder.ASC, s -> s.processInstanceKey().asc()),
        new FlowNodeInstanceSortTest.TestArguments(
            "processDefinitionKey", SortOrder.ASC, s -> s.processDefinitionKey().asc()),
        new FlowNodeInstanceSortTest.TestArguments(
            "bpmnProcessId", SortOrder.ASC, s -> s.bpmnProcessId().asc()),
        new FlowNodeInstanceSortTest.TestArguments(
            "startDate", SortOrder.ASC, s -> s.startDate().asc()),
        new FlowNodeInstanceSortTest.TestArguments(
            "endDate", SortOrder.ASC, s -> s.endDate().asc()),
        new FlowNodeInstanceSortTest.TestArguments(
            "flowNodeId", SortOrder.ASC, s -> s.flowNodeId().asc()),
        new FlowNodeInstanceSortTest.TestArguments("type", SortOrder.ASC, s -> s.type().asc()),
        new FlowNodeInstanceSortTest.TestArguments("state", SortOrder.ASC, s -> s.state().asc()),
        new FlowNodeInstanceSortTest.TestArguments(
            "incidentKey", SortOrder.DESC, s -> s.incidentKey().desc()),
        new FlowNodeInstanceSortTest.TestArguments(
            "tenantId", SortOrder.DESC, s -> s.tenantId().desc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      final String field,
      final SortOrder sortOrder,
      final Function<FlowNodeInstanceSort.Builder, ObjectBuilder<FlowNodeInstanceSort>> fn) {
    // when
    services.search(SearchQueryBuilders.flownodeInstanceSearchQuery(q -> q.sort(fn)));

    // then
    final var searchRequest = client.getSingleSearchRequest();

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
      Function<FlowNodeInstanceSort.Builder, ObjectBuilder<FlowNodeInstanceSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
