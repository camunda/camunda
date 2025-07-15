/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class MessageSubscriptionSearchTest {

  private static final int NUMBER_OF_MESSAGE_SUBSCRIPTIONS = 3;
  private static List<MessageSubscription> orderedMessageSubscriptions;

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    final var processes = List.of("process_with_parallel_receive_tasks.bpmn");
    processes.forEach(
        process ->
            deployResource(camundaClient, String.format("process/%s", process)).getProcesses());

    waitForProcessesToBeDeployed(camundaClient, processes.size());

    startProcessInstance(camundaClient, "process_with_parallel_receive_tasks");

    waitForProcessInstancesToStart(camundaClient, 1);

    waitForMessageSubscriptions(camundaClient, NUMBER_OF_MESSAGE_SUBSCRIPTIONS);

    orderedMessageSubscriptions =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .sort(s -> s.messageSubscriptionKey().asc())
            .send()
            .join()
            .items();
  }

  @Test
  void shouldReturnAllByDefault() {
    // Given / When
    final var searchResponse = camundaClient.newMessageSubscriptionSearchRequest().send().join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(NUMBER_OF_MESSAGE_SUBSCRIPTIONS);
    assertThat(searchResponse.page().totalItems()).isEqualTo(NUMBER_OF_MESSAGE_SUBSCRIPTIONS);
    assertThat(searchResponse.items())
        .containsExactlyInAnyOrderElementsOf(orderedMessageSubscriptions);
  }

  @Test
  void shouldFilterUsingAllFilters() {
    // Given
    final var expectedMessageSubscription = orderedMessageSubscriptions.getLast();

    // When
    final var searchResponse =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(
                f ->
                    f.messageSubscriptionKey(
                            expectedMessageSubscription.getMessageSubscriptionKey())
                        .processDefinitionId(expectedMessageSubscription.getProcessDefinitionId())
                        .processInstanceKey(expectedMessageSubscription.getProcessInstanceKey())
                        .elementId(expectedMessageSubscription.getElementId())
                        .elementInstanceKey(expectedMessageSubscription.getElementInstanceKey())
                        .messageSubscriptionType(
                            expectedMessageSubscription.getMessageSubscriptionType())
                        .lastUpdatedDate(
                            OffsetDateTime.parse(expectedMessageSubscription.getLastUpdatedDate()))
                        .messageName(expectedMessageSubscription.getMessageName())
                        .correlationKey(expectedMessageSubscription.getCorrelationKey())
                        .tenantId(expectedMessageSubscription.getTenantId()))
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(1);
    assertThat(searchResponse.items().getFirst()).isEqualTo(expectedMessageSubscription);
  }

  @Test
  void shouldSortByKeyDescending() {
    // Given / When
    final var searchResponse =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .sort(s -> s.messageSubscriptionKey().desc())
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).size().isEqualTo(NUMBER_OF_MESSAGE_SUBSCRIPTIONS);
    assertThat(searchResponse.items())
        .containsExactlyElementsOf(
            orderedMessageSubscriptions.stream()
                .sorted(Comparator.comparingLong(inc -> -inc.getMessageSubscriptionKey()))
                .toList());
  }

  @Test
  void shouldPaginateWithLimitAndCursor() {
    // Given
    final var response1 =
        camundaClient.newMessageSubscriptionSearchRequest().page(p -> p.limit(1)).send().join();
    final var response2 =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .page(p -> p.after(response1.page().endCursor()))
            .send()
            .join();

    // Then
    assertThat(response1.page().totalItems()).isEqualTo(NUMBER_OF_MESSAGE_SUBSCRIPTIONS);
    assertThat(response1.items()).containsExactly(orderedMessageSubscriptions.getFirst());

    assertThat(response2.page().totalItems()).isEqualTo(NUMBER_OF_MESSAGE_SUBSCRIPTIONS);
    assertThat(response2.items())
        .containsExactly(orderedMessageSubscriptions.get(1), orderedMessageSubscriptions.get(2));
  }
}
