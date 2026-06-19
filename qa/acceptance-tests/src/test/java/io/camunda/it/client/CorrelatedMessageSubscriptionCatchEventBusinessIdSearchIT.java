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
import static io.camunda.it.util.TestHelper.waitForMessageSubscriptions;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.CorrelatedMessageSubscription;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the businessId of the subscribing process instance flows end to end (engine ->
 * exporter -> secondary storage -> search API) for non-start correlations to a catch / boundary /
 * intermediate event, not only for message-start-event correlations. The businessId is captured
 * from the process instance at subscription-open time and carried onto the {@code
 * ProcessMessageSubscription:CORRELATED} event, so the correlated-message search exposes it for
 * these correlations too.
 *
 * <p>Intentionally a plain {@code @MultiDbTest} (not {@code @CompatibilityTest}): carrying the
 * subscribing instance's businessId onto the catch-event correlation is new in 8.10, so it cannot
 * be asserted against an older broker that does not emit it.
 */
@MultiDbTest
public class CorrelatedMessageSubscriptionCatchEventBusinessIdSearchIT {

  private static final String START_MESSAGE_NAME = "Start";
  private static final String BUSINESS_ID = "catch-order-1";
  private static final int RECEIVE_TASK_COUNT = 3;
  // one start-event correlation + one per correlated receive task
  private static final int EXPECTED_CORRELATIONS = RECEIVE_TASK_COUNT + 1;

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    deployResource(
        camundaClient, "process/process_with_message_start_and_parallel_receive_tasks.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    // start a single instance through the message start event carrying a business id; the instance
    // then waits on the three parallel receive tasks, whose subscriptions capture that business id
    camundaClient
        .newCorrelateMessageCommand()
        .messageName(START_MESSAGE_NAME)
        .withoutCorrelationKey()
        .businessId(BUSINESS_ID)
        .execute();
    waitForProcessInstancesToStart(camundaClient, 1);
    waitForMessageSubscriptions(camundaClient, RECEIVE_TASK_COUNT);

    // correlate each receive-task message to the waiting instance
    for (int i = 1; i <= RECEIVE_TASK_COUNT; i++) {
      camundaClient
          .newCorrelateMessageCommand()
          .messageName("Test" + i)
          .correlationKey("correlation_key_" + i)
          .send()
          .join();
    }

    waitForCorrelatedMessageSubscriptions(camundaClient, EXPECTED_CORRELATIONS);
  }

  @Test
  void shouldExposeBusinessIdOnCatchEventCorrelations() {
    // when
    final var searchResponse =
        camundaClient.newCorrelatedMessageSubscriptionSearchRequest().send().join();

    // then every correlation — the start event and the three catch events — carries the subscribing
    // instance's business id
    assertThat(searchResponse.items())
        .hasSize(EXPECTED_CORRELATIONS)
        .allSatisfy(s -> assertThat(s.getBusinessId()).isEqualTo(BUSINESS_ID));

    // and specifically the catch-event correlations (identified by a non-null elementInstanceKey,
    // which start-event correlations do not have) carry it — this is the path that previously
    // dropped the business id
    assertThat(searchResponse.items())
        .filteredOn(s -> s.getElementInstanceKey() != null)
        .hasSize(RECEIVE_TASK_COUNT)
        .allSatisfy(s -> assertThat(s.getBusinessId()).isEqualTo(BUSINESS_ID));
  }

  @Test
  void shouldFilterCatchEventCorrelationsByBusinessId() {
    // when
    final var searchResponse =
        camundaClient
            .newCorrelatedMessageSubscriptionSearchRequest()
            .filter(f -> f.businessId(BUSINESS_ID))
            .send()
            .join();

    // then all correlations are returned, including the catch-event ones
    assertThat(searchResponse.items())
        .hasSize(EXPECTED_CORRELATIONS)
        .extracting(CorrelatedMessageSubscription::getBusinessId)
        .containsOnly(BUSINESS_ID);
  }
}
