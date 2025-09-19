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
import io.camunda.search.sort.CorrelatedMessageSubscriptionSort;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CorrelatedMessageSubscriptionSortTest extends AbstractSortTransformerTest {

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments("correlationKey", SortOrder.ASC, s -> s.correlationKey().asc()),
        new TestArguments("correlationTime", SortOrder.ASC, s -> s.correlationTime().asc()),
        new TestArguments("flowNodeId", SortOrder.ASC, s -> s.flowNodeId().asc()),
        new TestArguments("flowNodeInstanceKey", SortOrder.ASC, s -> s.flowNodeInstanceKey().asc()),
        new TestArguments("messageKey", SortOrder.ASC, s -> s.messageKey().asc()),
        new TestArguments("messageName", SortOrder.ASC, s -> s.messageName().asc()),
        new TestArguments("partitionId", SortOrder.ASC, s -> s.partitionId().asc()),
        new TestArguments("bpmnProcessId", SortOrder.ASC, s -> s.processDefinitionId().asc()),
        new TestArguments(
            "processDefinitionKey", SortOrder.ASC, s -> s.processDefinitionKey().asc()),
        new TestArguments("processInstanceKey", SortOrder.ASC, s -> s.processInstanceKey().asc()),
        new TestArguments("subscriptionKey", SortOrder.ASC, s -> s.subscriptionKey().asc()),
        new TestArguments("tenantId", SortOrder.ASC, s -> s.tenantId().asc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      final String field,
      final SortOrder sortOrder,
      final Function<
              CorrelatedMessageSubscriptionSort.Builder,
              ObjectBuilder<CorrelatedMessageSubscriptionSort>>
          fn) {
    // when
    final var request =
        SearchQueryBuilders.correlatedMessageSubscriptionSearchQuery(q -> q.sort(fn));
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
              assertThat(t.field().field()).isEqualTo("messageKey");
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  private record TestArguments(
      String field,
      SortOrder sortOrder,
      Function<
              CorrelatedMessageSubscriptionSort.Builder,
              ObjectBuilder<CorrelatedMessageSubscriptionSort>>
          fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
