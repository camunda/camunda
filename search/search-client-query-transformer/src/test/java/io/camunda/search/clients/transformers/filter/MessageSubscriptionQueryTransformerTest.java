/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MessageSubscriptionQueryTransformerTest extends AbstractTransformerTest {

  @ParameterizedTest
  @MethodSource("queryFilterParameters")
  void shouldQueryByMessageSubscriptionKey(
      final Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>
          filterFunction,
      final String expectedFieldName,
      final Object expectedValue) {
    // Given
    final var filter = FilterBuilders.messageSubscription(filterFunction);

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            query -> {
              assertThat(query.must().size()).isEqualTo(2);
              assertThat(query.must().getFirst().queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      termQuery -> {
                        assertThat(termQuery.field()).isEqualTo("eventSourceType");
                        assertThat(termQuery.value().stringValue())
                            .isEqualTo("PROCESS_MESSAGE_SUBSCRIPTION");
                      });
              assertThat(query.must().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      termQuery -> {
                        assertThat(termQuery.field()).isEqualTo(expectedFieldName);
                        assertThat(termQuery.value().value()).isEqualTo(expectedValue);
                      });
            });
  }

  private static Stream<Arguments> queryFilterParameters() {
    return Stream.of(
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.messageSubscriptionKeys(123L),
            "key",
            123L),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.processDefinitionIds("abcd"),
            "bpmnProcessId",
            "abcd"),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.processDefinitionKeys(456L),
            "processDefinitionKey",
            456L),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.processInstanceKeys(999L),
            "processInstanceKey",
            999L),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.flowNodeIds("abcd"),
            "flowNodeId",
            "abcd"),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.flowNodeInstanceKeys(784L),
            "flowNodeInstanceKey",
            784L),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.messageSubscriptionTypes("CREATED"),
            "eventType",
            "CREATED"),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.dateTimes(OffsetDateTime.parse("2024-07-24T00:00Z")),
            "dateTime",
            OffsetDateTime.parse("2024-07-24T00:00Z")
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.messageNames("msg_name"),
            "metadata.messageName",
            "msg_name"),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.correlationKeys("key1"),
            "metadata.correlationKey",
            "key1"),
        Arguments.of(
            (Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>)
                b -> b.tenantIds("tnt1"),
            "tenantId",
            "tnt1"));
  }
}
