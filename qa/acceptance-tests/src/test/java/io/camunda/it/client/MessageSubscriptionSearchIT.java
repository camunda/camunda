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
import static io.camunda.it.util.TestHelper.startProcessInstanceWithMessage;
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.enums.MessageSubscriptionType;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class MessageSubscriptionSearchIT {

  private static final int NUMBER_OF_MESSAGE_SUBSCRIPTIONS = 5;
  private static List<MessageSubscription> orderedMessageSubscriptions;

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    final var processes =
        List.of(
            "process_with_parallel_receive_tasks.bpmn",
            "process_with_message_start_zeebe_properties.bpmn");
    processes.forEach(
        process ->
            deployResource(camundaClient, String.format("process/%s", process)).getProcesses());

    waitForProcessesToBeDeployed(camundaClient, processes.size());

    startProcessInstance(camundaClient, "process_with_parallel_receive_tasks");
    startProcessInstanceWithMessage(
        camundaClient, "process_with_message_start_zeebe_properties_Start");

    waitForProcessInstancesToStart(camundaClient, 2);

    waitForMessageSubscriptions(camundaClient, NUMBER_OF_MESSAGE_SUBSCRIPTIONS);

    camundaClient
        .newCorrelateMessageCommand()
        .messageName("Message1")
        .correlationKey("correlation_key_1")
        .send()
        .join();

    waitForMessageSubscriptions(
        camundaClient, f -> f.messageSubscriptionState(MessageSubscriptionState.CORRELATED), 2);

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
    assertThat(searchResponse.items()).hasSize(NUMBER_OF_MESSAGE_SUBSCRIPTIONS);
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
                        .processDefinitionKey(expectedMessageSubscription.getProcessDefinitionKey())
                        .processInstanceKey(expectedMessageSubscription.getProcessInstanceKey())
                        .elementId(expectedMessageSubscription.getElementId())
                        .elementInstanceKey(expectedMessageSubscription.getElementInstanceKey())
                        .messageSubscriptionState(
                            expectedMessageSubscription.getMessageSubscriptionState())
                        .lastUpdatedDate(expectedMessageSubscription.getLastUpdatedDate())
                        .messageName(expectedMessageSubscription.getMessageName())
                        .correlationKey(expectedMessageSubscription.getCorrelationKey())
                        .tenantId(expectedMessageSubscription.getTenantId()))
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).hasSize(1);
    assertThat(searchResponse.items().getFirst()).isEqualTo(expectedMessageSubscription);
  }

  @Test
  void shouldFilterCorrelatedSubscriptions() {
    // When
    final var searchResponse =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(f -> f.messageSubscriptionState(MessageSubscriptionState.CORRELATED))
            .sort(s -> s.messageName().asc())
            .send()
            .join();

    // Then
    assertThat(searchResponse.items()).hasSize(2);
    assertThat(searchResponse.items().getFirst())
        .extracting("messageName", "messageSubscriptionState")
        .containsExactly("Message1", MessageSubscriptionState.CORRELATED);
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
    assertThat(searchResponse.items()).hasSize(NUMBER_OF_MESSAGE_SUBSCRIPTIONS);
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
        .containsExactly(
            orderedMessageSubscriptions.get(1),
            orderedMessageSubscriptions.get(2),
            orderedMessageSubscriptions.get(3),
            orderedMessageSubscriptions.get(4));
  }

  @Test
  void shouldReturnStartEventSubscriptionWhenFilteredByType() {
    // when
    final var result =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(f -> f.messageSubscriptionType(MessageSubscriptionType.START_EVENT))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getMessageSubscriptionType())
        .isEqualTo(MessageSubscriptionType.START_EVENT);
    assertThat(result.items().getFirst().getElementId()).isEqualTo("StartEvent_msg");
    assertThat(result.items().getFirst().getMessageName())
        .isEqualTo("process_with_message_start_zeebe_properties_Start");
    assertThat(result.items().getFirst().getProcessInstanceKey()).isNull();
    assertThat(result.items().getFirst().getElementInstanceKey()).isNull();
  }

  @Test
  void shouldReturnIntermediateEventSubscriptionWhenFilteredByType() {
    // when
    final var result =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(
                f ->
                    f.messageSubscriptionType(MessageSubscriptionType.PROCESS_EVENT)
                        .messageName("process_with_message_start_zeebe_properties_Intermediate"))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getMessageSubscriptionType())
        .isEqualTo(MessageSubscriptionType.PROCESS_EVENT);
    assertThat(result.items().getFirst().getElementId()).isEqualTo("IntermediateCatch_1");
    assertThat(result.items().getFirst().getMessageName())
        .isEqualTo("process_with_message_start_zeebe_properties_Intermediate");
  }

  @Test
  @Disabled("Will be re-eavaluated once we start exporting extensionProperties again")
  void shouldReturnExtensionPropertiesForStartEventSubscription() {
    // when
    final var result =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(f -> f.messageSubscriptionType(MessageSubscriptionType.START_EVENT))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var sub = result.items().getFirst();
    assertThat(sub.getExtensionProperties())
        .containsEntry("customKey", "customValue")
        .containsEntry("env", "test");
  }

  @Test
  void shouldReturnProcessDefinitionNameForStartEventSubscription() {
    // when
    final var result =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(f -> f.messageSubscriptionType(MessageSubscriptionType.START_EVENT))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    final var sub = result.items().getFirst();
    assertThat(sub.getProcessDefinitionName()).isEqualTo("Phase 2 Test Process");
    assertThat(sub.getProcessDefinitionVersion()).isEqualTo(1);
  }

  @Test
  void shouldFilterByProcessDefinitionName() {
    // when
    final var result =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(f -> f.processDefinitionName("Phase 2 Test Process"))
            .send()
            .join();

    // then — both subscriptions (start and intermediate) belong to this process
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .allMatch(s -> "Phase 2 Test Process".equals(s.getProcessDefinitionName()));
  }
}
