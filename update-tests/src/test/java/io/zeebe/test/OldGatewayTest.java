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
final class OldGatewayTest {

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
  @SuppressWarnings("java:S2699") // there is an assertion when awaiting completion
  void update(final String name, final UpdateTestCase testCase, final ContainerState state) {
    // given
    state
        .withNetwork(network)
        .broker(CURRENT_VERSION)
        .withStandaloneGateway(LAST_VERSION)
        .start(true);
    final long wfInstanceKey = testCase.setUp(state.client());

    // when
    final long key = testCase.runBefore(state);

    // then
    testCase.runAfter(state, wfInstanceKey, key);
    assertThat(state).eventuallyHasCompletedProcess(PROCESS_ID);
  }
}
