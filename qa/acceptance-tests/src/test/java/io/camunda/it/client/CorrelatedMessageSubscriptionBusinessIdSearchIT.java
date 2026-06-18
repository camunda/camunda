/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.waitForCorrelatedMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.CorrelatedMessageSubscription;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the businessId carried by a message-start-event correlation flows end to end (engine ->
 * exporter -> secondary storage -> search API) and can be read back and filtered on every secondary
 * storage backend.
 *
 * <p>Intentionally a plain {@code @MultiDbTest} (not {@code @CompatibilityTest}): the start-event
 * businessId is new in 8.10, so it cannot be asserted against an older broker that does not emit
 * it.
 */
@MultiDbTest
public class CorrelatedMessageSubscriptionBusinessIdSearchIT {

  private static final String START_MESSAGE_NAME = "Start";
  private static final String BUSINESS_ID_A = "order-100";
  private static final String BUSINESS_ID_B = "order-200";

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    deployResource(
        camundaClient, "process/process_with_message_start_and_parallel_receive_tasks.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    // start two instances through the message start event, each carrying a distinct business id
    startProcessViaMessageStartWithBusinessId(BUSINESS_ID_A);
    startProcessViaMessageStartWithBusinessId(BUSINESS_ID_B);

    waitForProcessInstancesToStart(camundaClient, 2);
    // only the two start-event correlations are CORRELATED; the receive-task subscriptions stay
    // CREATED and therefore do not show up in the correlated-message search
    waitForCorrelatedMessageSubscriptions(camundaClient, 2);
  }

  @Test
  void shouldExposeBusinessIdOnStartEventCorrelation() {
    // when
    final var searchResponse =
        camundaClient.newCorrelatedMessageSubscriptionSearchRequest().send().join();

    // then
    assertThat(searchResponse.items())
        .extracting(CorrelatedMessageSubscription::getBusinessId)
        .containsExactlyInAnyOrder(BUSINESS_ID_A, BUSINESS_ID_B);
  }

  @Test
  void shouldFilterByBusinessIdEquals() {
    // when
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSubscriptionSearchRequest()
            .filter(f -> f.businessId(BUSINESS_ID_A))
            .send()
            .join();

    // then
    assertThat(searchResponse.items()).hasSize(1);
    assertThat(searchResponse.items().getFirst().getBusinessId()).isEqualTo(BUSINESS_ID_A);
  }

  @Test
  void shouldFilterByBusinessIdLikeWildcard() {
    // when
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSubscriptionSearchRequest()
            .filter(f -> f.businessId(b -> b.like("order-*")))
            .send()
            .join();

    // then
    assertThat(searchResponse.items())
        .extracting(CorrelatedMessageSubscription::getBusinessId)
        .containsExactlyInAnyOrder(BUSINESS_ID_A, BUSINESS_ID_B);
  }

  @Test
  void shouldReturnEmptyForNonMatchingBusinessId() {
    // when
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSubscriptionSearchRequest()
            .filter(f -> f.businessId("does-not-exist"))
            .send()
            .join();

    // then
    assertThat(searchResponse.items()).isEmpty();
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
