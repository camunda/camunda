/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.IncidentSort;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class IncidentSortTest extends AbstractSortTransformerTest {

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments("key", SortOrder.ASC, s -> s.incidentKey().asc()),
        new TestArguments(
            "processDefinitionKey", SortOrder.ASC, s -> s.processDefinitionKey().asc()),
        new TestArguments("bpmnProcessId", SortOrder.ASC, s -> s.processDefinitionId().asc()),
        new TestArguments("processInstanceKey", SortOrder.ASC, s -> s.processInstanceKey().asc()),
        new TestArguments("errorType", SortOrder.ASC, s -> s.errorType().asc()),
        new TestArguments("errorMessage", SortOrder.ASC, s -> s.errorMessage().asc()),
        new TestArguments("flowNodeId", SortOrder.ASC, s -> s.flowNodeId().asc()),
        new TestArguments("flowNodeInstanceKey", SortOrder.ASC, s -> s.flowNodeInstanceKey().asc()),
        new TestArguments("tenantId", SortOrder.ASC, s -> s.tenantId().asc()),
        new TestArguments("creationTime", SortOrder.ASC, s -> s.creationTime().asc()),
        new TestArguments("state", SortOrder.ASC, s -> s.state().asc()),
        new TestArguments("jobKey", SortOrder.ASC, s -> s.jobKey().asc()),
        new TestArguments("treePath", SortOrder.ASC, s -> s.treePath().asc()),
        new TestArguments("tenantId", SortOrder.ASC, s -> s.tenantId().asc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      final String field,
      final SortOrder sortOrder,
      final Function<IncidentSort.Builder, ObjectBuilder<IncidentSort>> fn) {
    // when
    final var request = SearchQueryBuilders.incidentSearchQuery(q -> q.sort(fn));
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).hasSize(2);
    Assertions.assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              Assertions.assertThat(t.field().field()).isEqualTo(field);
              Assertions.assertThat(t.field().order()).isEqualTo(sortOrder);
            });
    Assertions.assertThat(sort.get(1))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              Assertions.assertThat(t.field().field()).isEqualTo("key");
              Assertions.assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  private record TestArguments(
      String field,
      SortOrder sortOrder,
      Function<IncidentSort.Builder, ObjectBuilder<IncidentSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
