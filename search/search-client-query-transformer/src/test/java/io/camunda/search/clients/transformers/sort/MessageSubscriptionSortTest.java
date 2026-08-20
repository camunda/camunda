/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.sort.MessageSubscriptionSort;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MessageSubscriptionSortTest extends AbstractSortTransformerTest {

  private static Stream<Arguments> provideSortParameters() {
    return Stream.of(
        new TestArguments("key", SortOrder.ASC, s -> s.messageSubscriptionKey().asc()),
        new TestArguments("bpmnProcessId", SortOrder.ASC, s -> s.processDefinitionId().asc()),
        new TestArguments("processInstanceKey", SortOrder.ASC, s -> s.processInstanceKey().asc()),
        new TestArguments("flowNodeId", SortOrder.ASC, s -> s.flowNodeId().asc()),
        new TestArguments("flowNodeInstanceKey", SortOrder.ASC, s -> s.flowNodeInstanceKey().asc()),
        new TestArguments("eventType", SortOrder.ASC, s -> s.messageSubscriptionState().asc()),
        new TestArguments(
            "messageSubscriptionType", SortOrder.ASC, s -> s.messageSubscriptionType().asc()),
        new TestArguments(
            "processDefinitionName", SortOrder.ASC, s -> s.processDefinitionName().asc()),
        new TestArguments(
            "processDefinitionVersion", SortOrder.ASC, s -> s.processDefinitionVersion().asc()),
        new TestArguments("dateTime", SortOrder.ASC, s -> s.dateTime().asc()),
        new TestArguments("metadata.messageName", SortOrder.ASC, s -> s.messageName().asc()),
        new TestArguments("metadata.correlationKey", SortOrder.ASC, s -> s.correlationKey().asc()),
        new TestArguments("tenantId", SortOrder.ASC, s -> s.tenantId().asc()),
        new TestArguments("toolName", SortOrder.ASC, s -> s.toolName().asc()),
        new TestArguments(
            "inboundConnectorType", SortOrder.DESC, s -> s.inboundConnectorType().desc()));
  }

  @ParameterizedTest
  @MethodSource("provideSortParameters")
  public void shouldSortByField(
      final String expectedField,
      final SortOrder sortOrder,
      final Function<MessageSubscriptionSort.Builder, ObjectBuilder<MessageSubscriptionSort>> fn) {
    // when
    final var request = MessageSubscriptionQuery.of(q -> q.sort(fn));
    final var sort = transformRequest(request);

    // then
    assertThat(sort).hasSize(2);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo(expectedField);
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
      Function<MessageSubscriptionSort.Builder, ObjectBuilder<MessageSubscriptionSort>> fn)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {field, sortOrder, fn};
    }
  }
}
