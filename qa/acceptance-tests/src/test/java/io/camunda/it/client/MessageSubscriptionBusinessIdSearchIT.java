/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.MessageSubscriptionType;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the businessId of the subscribing process instance flows end to end (engine ->
 * exporter -> secondary storage -> search API) for the {@code /v2/message-subscriptions/search}
 * endpoint, and can be read back, filtered, and sorted on every secondary storage backend.
 *
 * <p>Two instances are started through a message start event, each carrying a distinct business id;
 * each instance then parks on three parallel receive tasks, whose {@code PROCESS_EVENT} message
 * subscriptions capture that business id when they are opened.
 *
 * <p>Intentionally a plain {@code @MultiDbTest} (not {@code @CompatibilityTest}): stamping the
 * subscribing instance's businessId onto its message subscriptions is new in 8.10, so it cannot be
 * asserted against an older broker that does not emit it.
 */
@MultiDbTest
public class MessageSubscriptionBusinessIdSearchIT {

  private static final String START_MESSAGE_NAME = "Start";
  private static final String BUSINESS_ID_A = "msgsub-order-100";
  private static final String BUSINESS_ID_B = "msgsub-order-200";
  private static final int RECEIVE_TASKS_PER_INSTANCE = 3;
  private static final int EXPECTED_PROCESS_EVENT_SUBSCRIPTIONS = 2 * RECEIVE_TASKS_PER_INSTANCE;

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    deployResource(
        camundaClient, "process/process_with_message_start_and_parallel_receive_tasks.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    // start two instances through the message start event, each carrying a distinct business id;
    // each instance parks on the three parallel receive tasks, whose subscriptions capture it
    startProcessViaMessageStartWithBusinessId(BUSINESS_ID_A);
    startProcessViaMessageStartWithBusinessId(BUSINESS_ID_B);

    waitForProcessInstancesToStart(camundaClient, 2);
    waitForMessageSubscriptions(
        camundaClient,
        f -> f.messageSubscriptionType(MessageSubscriptionType.PROCESS_EVENT),
        EXPECTED_PROCESS_EVENT_SUBSCRIPTIONS);
  }

  @Test
  void shouldExposeBusinessIdOnMessageSubscriptions() {
    // when
    final var processEventSubscriptions = searchProcessEventSubscriptions();

    // then each receive-task subscription carries its subscribing instance's business id
    assertThat(processEventSubscriptions)
        .hasSize(EXPECTED_PROCESS_EVENT_SUBSCRIPTIONS)
        .extracting(MessageSubscription::getBusinessId)
        .containsExactlyInAnyOrder(
            BUSINESS_ID_A,
            BUSINESS_ID_A,
            BUSINESS_ID_A,
            BUSINESS_ID_B,
            BUSINESS_ID_B,
            BUSINESS_ID_B);
  }

  @Test
  void shouldFilterByBusinessIdEquals() {
    // when
    final var searchResponse =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(f -> f.businessId(BUSINESS_ID_A))
            .send()
            .join();

    // then only the subscriptions of the instance carrying that business id are returned
    assertThat(searchResponse.items())
        .hasSize(RECEIVE_TASKS_PER_INSTANCE)
        .extracting(MessageSubscription::getBusinessId)
        .containsOnly(BUSINESS_ID_A);
  }

  @Test
  void shouldFilterByBusinessIdLikeWildcard() {
    // when
    final var searchResponse =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(f -> f.businessId(b -> b.like("msgsub-order-*")))
            .send()
            .join();

    // then both instances' subscriptions match the wildcard
    assertThat(searchResponse.items())
        .hasSize(EXPECTED_PROCESS_EVENT_SUBSCRIPTIONS)
        .extracting(MessageSubscription::getBusinessId)
        .containsOnly(BUSINESS_ID_A, BUSINESS_ID_B);
  }

  @Test
  void shouldFilterByBusinessIdExists() {
    // when
    final var searchResponse =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(f -> f.businessId(b -> b.exists(true)))
            .send()
            .join();

    // then only subscriptions that carry a business id are returned; the message-start-event
    // subscription has none and is excluded
    assertThat(searchResponse.items())
        .hasSize(EXPECTED_PROCESS_EVENT_SUBSCRIPTIONS)
        .extracting(MessageSubscription::getBusinessId)
        .containsOnly(BUSINESS_ID_A, BUSINESS_ID_B);
  }

  @Test
  void shouldReturnEmptyForNonMatchingBusinessId() {
    // when
    final var searchResponse =
        camundaClient
            .newMessageSubscriptionSearchRequest()
            .filter(f -> f.businessId("does-not-exist"))
            .send()
            .join();

    // then
    assertThat(searchResponse.items()).isEmpty();
  }

  @Test
  void shouldSortByBusinessId() {
    // when ascending
    final var ascending =
        processEventBusinessIds(
            camundaClient
                .newMessageSubscriptionSearchRequest()
                .sort(s -> s.businessId().asc())
                .send()
                .join()
                .items());

    // when descending
    final var descending =
        processEventBusinessIds(
            camundaClient
                .newMessageSubscriptionSearchRequest()
                .sort(s -> s.businessId().desc())
                .send()
                .join()
                .items());

    // then subscriptions are ordered by business id, with all of one instance before the other
    assertThat(ascending)
        .containsExactly(
            BUSINESS_ID_A,
            BUSINESS_ID_A,
            BUSINESS_ID_A,
            BUSINESS_ID_B,
            BUSINESS_ID_B,
            BUSINESS_ID_B);
    assertThat(descending)
        .containsExactly(
            BUSINESS_ID_B,
            BUSINESS_ID_B,
            BUSINESS_ID_B,
            BUSINESS_ID_A,
            BUSINESS_ID_A,
            BUSINESS_ID_A);
  }

  private static List<MessageSubscription> searchProcessEventSubscriptions() {
    return camundaClient
        .newMessageSubscriptionSearchRequest()
        .filter(f -> f.messageSubscriptionType(MessageSubscriptionType.PROCESS_EVENT))
        .send()
        .join()
        .items();
  }

  private static List<String> processEventBusinessIds(final List<MessageSubscription> items) {
    return items.stream()
        .filter(s -> s.getMessageSubscriptionType() == MessageSubscriptionType.PROCESS_EVENT)
        .map(MessageSubscription::getBusinessId)
        .toList();
  }

  private static void startProcessViaMessageStartWithBusinessId(final String businessId) {
    camundaClient
        .newCorrelateMessageCommand()
        .messageName(START_MESSAGE_NAME)
        .withoutCorrelationKey()
        .businessId(businessId)
        .execute();
  }
}
