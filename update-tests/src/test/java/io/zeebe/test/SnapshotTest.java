/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import static io.zeebe.test.ContainerStateAssert.assertThat;
import static io.zeebe.test.UpdateTestCaseProvider.PROCESS_ID;

import io.zeebe.test.util.asserts.EitherAssert;
import io.zeebe.util.VersionUtil;
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

  public static final String LAST_VERSION = VersionUtil.getPreviousVersion();
  public static final String CURRENT_VERSION = "current-test";
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
    state.withNetwork(network).broker(LAST_VERSION).start(true);
    final long wfInstanceKey = testCase.setUp(state.client());
    final long key = testCase.runBefore(state);

    // when
    // it's necessary to restart without the debug exporter to allow snapshotting
    state.close();
    state.broker(LAST_VERSION).start(false);
    // there's a slight chance that we'd processed everything before shutting down, so we send a
    // dummy message to ensure we have processed something since we recovered and so take a snapshot
    sendDummyMessageToEnforceSnapshot(state);
    EitherAssert.assertThat(state.getPartitionsActuatorClient().takeSnapshot())
        .as("expect successful response as right member")
        .isRight();
    assertThat(state).eventuallyHasSnapshotAvailable(1);

    // perform the update
    state.close();
    state.broker(CURRENT_VERSION).start(true);
    assertThat(state).hasSnapshotAvailable(1);

    // then
    testCase.runAfter(state, wfInstanceKey, key);
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
