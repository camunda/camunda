/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test;

import static io.camunda.zeebe.test.ContainerStateAssert.assertThat;
import static io.camunda.zeebe.test.UpdateTestCaseProvider.PROCESS_ID;

import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.containers.Network;

@ExtendWith(ContainerStateExtension.class)
final class SnapshotTest {

  private static Network network;

  @BeforeAll
  static void setUp() {
    network = Network.newNetwork();
  }

  @AfterAll
  static void tearDown() {
    Optional.ofNullable(network).ifPresent(Network::close);
  }

  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(UpdateTestCaseProvider.class)
  void update(final String name, final UpdateTestCase testCase, final ContainerState state) {
    // given
    state.withNetwork(network).withOldBroker().start(true);
    final long processInstanceKey = testCase.setUp(state.client());
    final long key = testCase.runBefore(state);

    // when
    EitherAssert.assertThat(state.getPartitionsActuatorClient().takeSnapshot())
        .as("expect successful response as right member")
        .isRight();
    assertThat(state).eventuallyHasSnapshotAvailable(1);

    // perform the update
    state.close();
    state.withNewBroker().start(true);
    assertThat(state).hasSnapshotAvailable(1);

    // then
    testCase.runAfter(state, processInstanceKey, key);
    assertThat(state).eventuallyHasCompletedProcess(PROCESS_ID);
  }

  private void sendDummyMessageToEnforceSnapshot(final ContainerState state) {
    state
        .client()
        .newPublishMessageCommand()
        .messageName("enforceSnapshot")
        .correlationKey("dummy")
        .send()
        .join(5, TimeUnit.SECONDS);
  }
}
