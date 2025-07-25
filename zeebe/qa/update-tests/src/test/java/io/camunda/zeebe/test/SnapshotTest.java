/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static io.camunda.zeebe.test.ContainerStateAssert.assertThat;
import static io.camunda.zeebe.test.UpdateTestCaseProvider.PROCESS_ID;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
    state.getPartitionsActuator().takeSnapshot();
    assertThat(state).eventuallyHasSnapshotAvailable(1);

    // perform the update
    state.close();
    state.withNewBroker().start(true);
    assertThat(state).hasSnapshotAvailable(1);

    // then
    testCase.runAfter(state, processInstanceKey, key);
    assertThat(state).eventuallyHasCompletedProcess(PROCESS_ID);
  }

  @Test
  void takesSnapshotAfterUpdate(final ContainerState state) {
    // given - we take an initial snapshot on the old version
    state.withNetwork(network).withOldBroker().start(true);
    state
        .client()
        .newPublishMessageCommand()
        .messageName("test")
        .correlationKey("key")
        .send()
        .join();
    state.getPartitionsActuator().takeSnapshot();
    assertThat(state).eventuallyHasSnapshotAvailable(1);
    final var oldSnapshotId = state.getPartitionsActuator().query().get(1).snapshotId();

    // when - we update to the new version without any new processing
    state.close();
    state.withNewBroker().start(true);

    // then - the new version takes a new snapshot to persist migrations and version marker
    Awaitility.await()
        .until(
            () -> state.getPartitionsActuator().query().get(1).snapshotId(),
            newSnapshotId -> !newSnapshotId.equals(oldSnapshotId));
  }
}
