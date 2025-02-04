/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.management;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert.RemoteJobStreamsAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class JobStreamEndpointIT {
  @TestZeebe(initMethod = "initTestCluster")
  private static TestCluster cluster;

  private final TestGateway<?> gateway = cluster.availableGateway();
  @AutoClose private final CamundaClient client = gateway.newClientBuilder().build();

  @SuppressWarnings("unused")
  static void initTestCluster() {
    cluster = TestCluster.builder().withGatewaysCount(2).withEmbeddedGateway(false).build();
  }

  @AfterEach
  void afterEach() {
    // ensure we close all open streams
    client.close();

    // avoid flakiness between tests by waiting until the registries are empty
    final var actuator = JobStreamActuator.of(gateway);
    Awaitility.await("until no streams are registered")
        .untilAsserted(
            () -> {
              JobStreamActuatorAssert.assertThat(actuator).remoteStreams().isEmpty();
              JobStreamActuatorAssert.assertThat(actuator).clientStreams().isEmpty();
            });
  }

  @Test
  void shouldListMultipleRemoteStreams() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .workerName("foo")
        .timeout(Duration.ofMillis(100))
        .fetchVariables("foo", "fooz")
        .send();
    client
        .newStreamJobsCommand()
        .jobType("bar")
        .consumer(ignored -> {})
        .workerName("bar")
        .timeout(Duration.ofMillis(250))
        .fetchVariables("bar", "barz")
        .send();

    // then
    final var brokerActuator = JobStreamActuator.of(cluster.brokers().get(MemberId.from("0")));
    Awaitility.await("until foo stream is registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(brokerActuator)
                    .remoteStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType("foo"),
                        RemoteJobStreamsAssert.hasWorker("foo"),
                        RemoteJobStreamsAssert.hasTimeout(100L),
                        RemoteJobStreamsAssert.hasFetchVariables("foo", "fooz")));
    Awaitility.await("until bar stream is registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(brokerActuator)
                    .remoteStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType("bar"),
                        RemoteJobStreamsAssert.hasWorker("bar"),
                        RemoteJobStreamsAssert.hasTimeout(250L),
                        RemoteJobStreamsAssert.hasFetchVariables("bar", "barz")));
  }

  @Test
  void shouldListMultipleRemoteConsumers() {
    // given
    //noinspection resource
    final var otherGateway =
        cluster.gateways().values().stream()
            .filter(g -> !g.nodeId().equals(gateway.nodeId()))
            .findAny()
            .orElseThrow();
    try (final var otherClient = otherGateway.newClientBuilder().build()) {
      client
          .newStreamJobsCommand()
          .jobType("foo")
          .consumer(ignored -> {})
          .workerName("foo")
          .timeout(Duration.ofMillis(100))
          .fetchVariables("foo", "fooz")
          .send();
      otherClient
          .newStreamJobsCommand()
          .jobType("foo")
          .consumer(ignored -> {})
          .workerName("foo")
          .timeout(Duration.ofMillis(100))
          .fetchVariables("foo", "fooz")
          .send();

      // then
      final var brokerActuator = JobStreamActuator.of(cluster.brokers().get(MemberId.from("0")));
      Awaitility.await("until all streams are registered")
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(brokerActuator)
                      .remoteStreams()
                      .haveConsumerReceiver(
                          1, "foo", gateway.nodeId().id(), otherGateway.nodeId().id()));
    }
  }

  @Test
  void shouldListMultipleClientStreams() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .workerName("foo")
        .timeout(Duration.ofMillis(100))
        .fetchVariables("foo", "fooz")
        .send();
    client
        .newStreamJobsCommand()
        .jobType("bar")
        .consumer(ignored -> {})
        .workerName("bar")
        .timeout(Duration.ofMillis(250))
        .fetchVariables("bar", "barz")
        .send();

    // then
    final var actuator = JobStreamActuator.of(gateway);
    Awaitility.await("until foo stream is registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .clientStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType("foo"),
                        RemoteJobStreamsAssert.hasWorker("foo"),
                        RemoteJobStreamsAssert.hasTimeout(100L),
                        RemoteJobStreamsAssert.hasFetchVariables("foo", "fooz")));
    Awaitility.await("until bar stream is registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .clientStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType("bar"),
                        RemoteJobStreamsAssert.hasWorker("bar"),
                        RemoteJobStreamsAssert.hasTimeout(250L),
                        RemoteJobStreamsAssert.hasFetchVariables("bar", "barz")));
  }
}
