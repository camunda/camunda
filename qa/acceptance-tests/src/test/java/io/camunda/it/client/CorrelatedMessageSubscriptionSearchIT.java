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
import static io.camunda.it.util.TestHelper.waitForCorrelatedMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.CorrelatedMessageSubscription;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class CorrelatedMessageSubscriptionSearchIT {

  private static final int NUMBER_OF_CORRELATED_MESSAGES = 8;
  private static List<CorrelatedMessageSubscription> orderedCorrelatedMessageSubscriptions;

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

    waitForCorrelatedMessageSubscriptions(camundaClient, NUMBER_OF_CORRELATED_MESSAGES);

    orderedCorrelatedMessageSubscriptions =
        camundaClient
            .newCorrelatedMessageSubscriptionSearchRequest()
            .sort(s -> s.messageKey().asc().subscriptionKey().asc())
            .send()
            .join()
            .items();
  }

  @Test
  void shouldReturnAllByDefault() {
    // Given / When
    final var searchResponse =
        camundaClient.newCorrelatedMessageSubscriptionSearchRequest().send().join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(searchResponse.page().totalItems()).isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(searchResponse.items())
        .containsExactlyInAnyOrderElementsOf(orderedCorrelatedMessageSubscriptions);
  }

  @Test
  void shouldFilterUsingAllFiltersForProcessMessageEvent() {
    // Given the last correlated message subscription which is from a process message event
    final var expectedCorrelatedMessageSubscription =
        orderedCorrelatedMessageSubscriptions.getLast();

    // When
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSubscriptionSearchRequest()
            .filter(
                f ->
                    f.correlationKey(expectedCorrelatedMessageSubscription.getCorrelationKey())
                        .correlationTime(expectedCorrelatedMessageSubscription.getCorrelationTime())
                        .elementId(expectedCorrelatedMessageSubscription.getElementId())
                        .elementInstanceKey(
                            expectedCorrelatedMessageSubscription.getElementInstanceKey())
                        .messageKey(expectedCorrelatedMessageSubscription.getMessageKey())
                        .messageName(expectedCorrelatedMessageSubscription.getMessageName())
                        .partitionId(expectedCorrelatedMessageSubscription.getPartitionId())
                        .processDefinitionId(
                            expectedCorrelatedMessageSubscription.getProcessDefinitionId())
                        .processInstanceKey(
                            expectedCorrelatedMessageSubscription.getProcessInstanceKey())
                        .subscriptionKey(expectedCorrelatedMessageSubscription.getSubscriptionKey())
                        .tenantId(expectedCorrelatedMessageSubscription.getTenantId()))
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(1);
    assertThat(searchResponse.items().getFirst()).isEqualTo(expectedCorrelatedMessageSubscription);
  }

  @Test
  void shouldFilterUsingAllFiltersForStartMessageEvent() {
    // Given the first correlated message subscription which is always for a start message event
    final var expectedCorrelatedMessageSubscription =
        orderedCorrelatedMessageSubscriptions.getFirst();

    // When
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSubscriptionSearchRequest()
            .filter(
                f ->
                    f.correlationKey(expectedCorrelatedMessageSubscription.getCorrelationKey())
                        .correlationTime(expectedCorrelatedMessageSubscription.getCorrelationTime())
                        .elementId(expectedCorrelatedMessageSubscription.getElementId())
                        .messageKey(expectedCorrelatedMessageSubscription.getMessageKey())
                        .messageName(expectedCorrelatedMessageSubscription.getMessageName())
                        .partitionId(expectedCorrelatedMessageSubscription.getPartitionId())
                        .processDefinitionId(
                            expectedCorrelatedMessageSubscription.getProcessDefinitionId())
                        .processDefinitionKey(
                            expectedCorrelatedMessageSubscription.getProcessDefinitionKey())
                        .processInstanceKey(
                            expectedCorrelatedMessageSubscription.getProcessInstanceKey())
                        .subscriptionKey(expectedCorrelatedMessageSubscription.getSubscriptionKey())
                        .tenantId(expectedCorrelatedMessageSubscription.getTenantId()))
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(1);
    assertThat(searchResponse.items().getFirst()).isEqualTo(expectedCorrelatedMessageSubscription);
  }

  @Test
  void shouldSortByKeyDescending() {
    // Given / When
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSubscriptionSearchRequest()
            .sort(s -> s.messageKey().desc().subscriptionKey().desc())
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(searchResponse.items())
        .containsExactlyElementsOf(
            orderedCorrelatedMessageSubscriptions.stream()
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
            .newCorrelatedMessageSubscriptionSearchRequest()
            .sort(s -> s.messageKey().asc().subscriptionKey().asc())
            .page(p -> p.limit(1))
            .send()
            .join();

    // When
    final var response2 =
        camundaClient
            .newCorrelatedMessageSubscriptionSearchRequest()
            .sort(s -> s.messageKey().asc().subscriptionKey().asc())
            .page(p -> p.after(response1.page().endCursor()))
            .send()
            .join();

    // Then
    assertThat(response1.page().totalItems()).isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(response1.items()).containsExactly(orderedCorrelatedMessageSubscriptions.getFirst());

    assertThat(response2.page().totalItems()).isEqualTo(NUMBER_OF_CORRELATED_MESSAGES);
    assertThat(response2.items())
        .hasSize(NUMBER_OF_CORRELATED_MESSAGES - 1)
        .containsExactlyElementsOf(
            orderedCorrelatedMessageSubscriptions.subList(1, NUMBER_OF_CORRELATED_MESSAGES));
  }
}
