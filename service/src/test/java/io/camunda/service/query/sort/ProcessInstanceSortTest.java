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
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.query.filter.ProcessInstanceSearchQueryStub;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.sort.ProcessInstanceSort;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ProcessInstanceSortTest {
  private ProcessInstanceServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new ProcessInstanceSearchQueryStub().registerWith(client);
    services = new ProcessInstanceServices(null, client);
  }

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new ProcessInstanceSortTest.TestArguments("key", SortOrder.ASC, s -> s.key().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "processName", SortOrder.DESC, s -> s.processName().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "processVersion", SortOrder.DESC, s -> s.processVersion().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "bpmnProcessId", SortOrder.ASC, s -> s.bpmnProcessId().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "parentProcessInstanceKey", SortOrder.ASC, s -> s.parentKey().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "parentFlowNodeInstanceKey", SortOrder.DESC, s -> s.parentFlowNodeInstanceKey().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "startDate", SortOrder.ASC, s -> s.startDate().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "endDate", SortOrder.DESC, s -> s.endDate().desc()),
        new ProcessInstanceSortTest.TestArguments("state", SortOrder.DESC, s -> s.state().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "hasActiveOperation", SortOrder.DESC, s -> s.hasActiveOperation().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "processDefinitionKey", SortOrder.DESC, s -> s.processDefinitionKey().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "tenantId", SortOrder.ASC, s -> s.tenantId().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "rootInstanceId", SortOrder.ASC, s -> s.rootInstanceId().asc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      final String field,
      final SortOrder sortOrder,
      final Function<ProcessInstanceSort.Builder, ObjectBuilder<ProcessInstanceSort>> fn) {
    // when
    services.search(SearchQueryBuilders.processInstanceSearchQuery(q -> q.sort(fn)));

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
      Function<ProcessInstanceSort.Builder, ObjectBuilder<ProcessInstanceSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
