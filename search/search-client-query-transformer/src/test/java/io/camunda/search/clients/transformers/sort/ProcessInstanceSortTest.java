/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ProcessInstanceSortTest extends AbstractSortTransformerTest {

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new ProcessInstanceSortTest.TestArguments(
            "key", SortOrder.ASC, s -> s.processInstanceKey().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "bpmnProcessId", SortOrder.ASC, s -> s.processDefinitionId().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "processName", SortOrder.DESC, s -> s.processDefinitionName().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "processVersion", SortOrder.DESC, s -> s.processDefinitionVersion().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "processVersionTag", SortOrder.ASC, s -> s.processDefinitionVersionTag().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "processDefinitionKey", SortOrder.ASC, s -> s.processDefinitionKey().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "rootProcessInstanceKey", SortOrder.ASC, s -> s.rootProcessInstanceKey().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "parentProcessInstanceKey", SortOrder.ASC, s -> s.parentProcessInstanceKey().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "parentFlowNodeInstanceKey", SortOrder.DESC, s -> s.parentFlowNodeInstanceKey().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "treePath", SortOrder.DESC, s -> s.treePath().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "startDate", SortOrder.ASC, s -> s.startDate().asc()),
        new ProcessInstanceSortTest.TestArguments(
            "endDate", SortOrder.DESC, s -> s.endDate().desc()),
        new ProcessInstanceSortTest.TestArguments("state", SortOrder.DESC, s -> s.state().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "incident", SortOrder.DESC, s -> s.hasIncident().desc()),
        new ProcessInstanceSortTest.TestArguments(
            "tenantId", SortOrder.ASC, s -> s.tenantId().asc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      final String field,
      final SortOrder sortOrder,
      final Function<ProcessInstanceSort.Builder, ObjectBuilder<ProcessInstanceSort>> fn) {
    // when
    final var request = SearchQueryBuilders.processInstanceSearchQuery(q -> q.sort(fn));
    final var sort = transformRequest(request);

    // then
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
