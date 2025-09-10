/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstanceWithMessage;
import static io.camunda.it.util.TestHelper.waitForCorrelatedMessages;
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.CorrelatedMessage;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class CorrelatedMessageSearchTest {

  private static final int NUMBER_OF_CORRELATED_MESSAGES = 8;
  private static List<CorrelatedMessage> orderedCorrelatedMessages;

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    deployResource(
        camundaClient, "process/process_with_message_start_and_parallel_receive_tasks.bpmn");
    deployResource(
        camundaClient, "process/process_with_message_start2_and_parallel_receive_tasks.bpmn");

    waitForProcessesToBeDeployed(camundaClient, 2);
    startProcessInstanceWithMessage(camundaClient, "Start");
    startProcessInstanceWithMessage(camundaClient, "Start2");
    waitForProcessInstancesToStart(camundaClient, 2);
    waitForMessageSubscriptions(camundaClient, 6);

    IntStream.range(1, 4)
        .forEach(
            i ->
                camundaClient
                    .newCorrelateMessageCommand()
                    .messageName("Test" + i)
                    .correlationKey("correlation_key_" + i)
                    .variables("{\"value\": " + i + "}")
                    .send()
                    .join());

    waitForCorrelatedMessages(camundaClient, NUMBER_OF_CORRELATED_MESSAGES);

    orderedCorrelatedMessages =
        camundaClient
            .newCorrelatedMessageSearchRequest()
            .sort(s -> s.messageKey().asc().subscriptionKey().asc())
            .send()
            .join()
            .items();
  }

  @Test
  void shouldReturnAllByDefault() {
    // Given / When
    final var searchResponse = camundaClient.newCorrelatedMessageSearchRequest().send().join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(searchResponse.page().totalItems()).isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(searchResponse.items())
        .containsExactlyInAnyOrderElementsOf(orderedCorrelatedMessages);
  }

  @Test
  void shouldFilterUsingAllFiltersForProcessMessageEvent() {
    // Given the last correlated message which is from a process message event
    final var expectedCorrelatedMessage = orderedCorrelatedMessages.getLast();

    // When
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSearchRequest()
            .filter(
                f ->
                    f.correlationKey(expectedCorrelatedMessage.getCorrelationKey())
                        .correlationTime(
                            OffsetDateTime.parse(expectedCorrelatedMessage.getCorrelationTime()))
                        .elementId(expectedCorrelatedMessage.getElementId())
                        .elementInstanceKey(expectedCorrelatedMessage.getElementInstanceKey())
                        .messageKey(expectedCorrelatedMessage.getMessageKey())
                        .messageName(expectedCorrelatedMessage.getMessageName())
                        .partitionId(expectedCorrelatedMessage.getPartitionId())
                        .processDefinitionId(expectedCorrelatedMessage.getProcessDefinitionId())
                        .processInstanceKey(expectedCorrelatedMessage.getProcessInstanceKey())
                        .subscriptionKey(expectedCorrelatedMessage.getSubscriptionKey())
                        .tenantId(expectedCorrelatedMessage.getTenantId()))
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(1);
    assertThat(searchResponse.items().getFirst()).isEqualTo(expectedCorrelatedMessage);
  }

  @Test
  void shouldFilterUsingAllFiltersForStartMessageEvent() {
    // Given the first correlated message which is always for a start message event
    final var expectedCorrelatedMessage = orderedCorrelatedMessages.getFirst();

    // When
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSearchRequest()
            .filter(
                f ->
                    f.correlationKey(expectedCorrelatedMessage.getCorrelationKey())
                        .correlationTime(
                            OffsetDateTime.parse(expectedCorrelatedMessage.getCorrelationTime()))
                        .elementId(expectedCorrelatedMessage.getElementId())
                        .messageKey(expectedCorrelatedMessage.getMessageKey())
                        .messageName(expectedCorrelatedMessage.getMessageName())
                        .partitionId(expectedCorrelatedMessage.getPartitionId())
                        .processDefinitionId(expectedCorrelatedMessage.getProcessDefinitionId())
                        .processDefinitionKey(expectedCorrelatedMessage.getProcessDefinitionKey())
                        .processInstanceKey(expectedCorrelatedMessage.getProcessInstanceKey())
                        .subscriptionKey(expectedCorrelatedMessage.getSubscriptionKey())
                        .tenantId(expectedCorrelatedMessage.getTenantId()))
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(1);
    assertThat(searchResponse.items().getFirst()).isEqualTo(expectedCorrelatedMessage);
  }

  @Test
  void shouldSortByKeyDescending() {
    // Given / When
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSearchRequest()
            .sort(s -> s.messageKey().desc().subscriptionKey().desc())
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(searchResponse.items())
        .containsExactlyElementsOf(
            orderedCorrelatedMessages.stream()
                .sorted(
                    (a, b) -> {
                      final int compareMessageKey =
                          Long.compare(b.getMessageKey(), a.getMessageKey());
                      return compareMessageKey == 0
                          ? Long.compare(b.getSubscriptionKey(), a.getSubscriptionKey())
                          : compareMessageKey;
                    })
                .toList());
  }

  @Test
  void shouldPaginateWithLimitAndCursor() {
    // Given
    final var response1 =
        camundaClient
            .newCorrelatedMessageSearchRequest()
            .sort(s -> s.messageKey().asc().subscriptionKey().asc())
            .page(p -> p.limit(1))
            .send()
            .join();

    // When
    final var response2 =
        camundaClient
            .newCorrelatedMessageSearchRequest()
            .sort(s -> s.messageKey().asc().subscriptionKey().asc())
            .page(p -> p.after(response1.page().endCursor()))
            .send()
            .join();

    // Then
    assertThat(response1.page().totalItems()).isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(response1.items()).containsExactly(orderedCorrelatedMessages.getFirst());

    assertThat(response2.page().totalItems()).isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(response2.items())
        .hasSize(NUMBER_OF_CORRELATED_MESSAGES - 1)
        .containsExactlyElementsOf(
            orderedCorrelatedMessages.subList(1, NUMBER_OF_CORRELATED_MESSAGES));
  }
}
