/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.jobstream.AbstractJobStreamsAssert;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class JobStreamLifecycleIT {
  @TestZeebe(initMethod = "initTestCluster", purgeAfterEach = false)
  private static TestCluster cluster;

  private final TestGateway<?> gateway = cluster.availableGateway();
  @AutoClose private final CamundaClient client = gateway.newClientBuilder().build();

  private final String jobType = Strings.newRandomValidBpmnId();

  @SuppressWarnings("unused")
  static void initTestCluster() {
    cluster =
        TestCluster.builder()
            .withReplicationFactor(2)
            .withBrokersCount(2)
            .withGatewaysCount(2)
            .withEmbeddedGateway(false)
            .build();
  }

  @Test
  void shouldRegisterStream() {
    // given
    final var command =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .fetchVariables("foo", "bar")
            .timeout(Duration.ofMillis(500))
            .workerName("worker");

    // when
    command.send();

    // then
    Awaitility.await("until stream is registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveExactlyAll(
                        1,
                        AbstractJobStreamsAssert.hasJobType(jobType),
                        AbstractJobStreamsAssert.hasWorker("worker"),
                        AbstractJobStreamsAssert.hasTimeout(500),
                        AbstractJobStreamsAssert.hasFetchVariables("foo", "bar")));
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var actuator = brokerActuator(nodeId);
      Awaitility.await("until stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(actuator)
                      .remoteStreams()
                      .haveExactlyAll(
                          1,
                          AbstractJobStreamsAssert.hasJobType(jobType),
                          AbstractJobStreamsAssert.hasWorker("worker"),
                          AbstractJobStreamsAssert.hasTimeout(500),
                          AbstractJobStreamsAssert.hasFetchVariables("foo", "bar")));
    }
  }

  @Test
  void shouldRegisterStreamsWithDifferentProperties() {
    // given - two logically DIFFERENT streams
    final var commandA =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .fetchVariables("foo", "bar")
            .timeout(Duration.ofMillis(500))
            .workerName("commandA");
    final var commandB =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .fetchVariables("foo", "bar")
            .timeout(Duration.ofMillis(500))
            .workerName("commandB");

    // when - both are registered individually on the gateway
    commandA.send();
    commandB.send();
    Awaitility.await("until streams are registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveJobType(2, jobType));

    // then - the streams are not aggregated on the broker side, as they are logically different
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var actuator = brokerActuator(nodeId);
      Awaitility.await("until stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(actuator)
                      .remoteStreams()
                      .haveJobType(2, jobType));
    }
  }

  @Test
  void shouldAggregateStreams() {
    // given - two logically equivalent streams
    final var commandA =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .fetchVariables("foo", "bar")
            .timeout(Duration.ofMillis(500))
            .workerName("command");
    final var commandB =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .fetchVariables("foo", "bar")
            .timeout(Duration.ofMillis(500))
            .workerName("command");

    // when - both streams are opened and registered on the gateway as individual client streams
    commandA.send();
    commandB.send();
    Awaitility.await("until streams are registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveJobType(2, jobType));

    // then - only one stream is registered on each broker as it is aggregated per gateway
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var actuator = brokerActuator(nodeId);
      Awaitility.await("until stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(actuator)
                      .remoteStreams()
                      .haveConsumerCount(1, jobType, 1));
    }
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/17513")
  void shouldAggregateStreamsEvenAcrossRestarts() {
    // given - many logically equivalent streams
    final List<JobWorker> workers = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      workers.add(
          client
              .newWorker()
              .jobType(jobType)
              .handler((c, j) -> {})
              .fetchVariables("foo", "bar")
              .timeout(Duration.ofMillis(500))
              .name("command")
              .streamEnabled(true)
              .open());
    }
    Awaitility.await("until streams are registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveJobType(100, jobType));

    // when - trigger stream restarts by restarting the gateway
    gateway.stop().start();
    cluster.awaitCompleteTopology();
    Awaitility.await("until streams are re-registered")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveJobType(100, jobType));

    // then - only one stream is registered on each broker as it is aggregated per gateway
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var actuator = brokerActuator(nodeId);
      Awaitility.await("until stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(actuator)
                      .remoteStreams()
                      .haveConsumerCount(1, jobType, 1));
    }
    CloseHelper.quietCloseAll(workers);
  }

  @Test
  void shouldAggregateStreamWithDifferentReceivers() {
    // given - two logically equivalent streams on different gateways
    //noinspection resource
    final var otherGateway =
        cluster.gateways().values().stream()
            .filter(g -> !g.nodeId().equals(gateway.nodeId()))
            .findAny()
            .orElseThrow();
    try (final var otherClient = otherGateway.newClientBuilder().build()) {
      final var commandA =
          client
              .newStreamJobsCommand()
              .jobType(jobType)
              .consumer(ignored -> {})
              .fetchVariables("foo", "bar")
              .timeout(Duration.ofMillis(500))
              .workerName("command");
      final var commandB =
          otherClient
              .newStreamJobsCommand()
              .jobType(jobType)
              .consumer(ignored -> {})
              .fetchVariables("foo", "bar")
              .timeout(Duration.ofMillis(500))
              .workerName("command");

      // when - both streams are opened and registered on the gateway as individual client streams
      commandA.send();
      commandB.send();
      Awaitility.await("until streams are registered")
          .untilAsserted(
              () -> {
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveJobType(1, jobType);
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(otherGateway))
                    .clientStreams()
                    .haveJobType(1, jobType);
              });

      // then - only one stream is registered on each broker as it is aggregated per gateway, with
      // two consumers
      for (int nodeId = 0; nodeId < 2; nodeId++) {
        final var actuator = brokerActuator(nodeId);
        Awaitility.await("until stream is registered on broker '%d'".formatted(nodeId))
            .untilAsserted(
                () ->
                    JobStreamActuatorAssert.assertThat(actuator)
                        .remoteStreams()
                        .haveConsumerCount(1, jobType, 2));
      }
    }
  }

  @Test
  void shouldRemoveStreamOnClientCancel() {
    // given - one stream is registered on the gateway
    final var stream =
        client.newStreamJobsCommand().jobType(jobType).consumer(ignored -> {}).send();
    Awaitility.await("until stream is fully registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveConnectedTo(1, jobType, 0, 1));

    // when - that stream is cancelled
    stream.cancel(true);

    // then - it is removed everywhere, as it was the last client for this logical stream
    Awaitility.await("until no gateway streams are registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .doNotHaveJobType(jobType));
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var actuator = brokerActuator(nodeId);
      Awaitility.await("until no stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(actuator)
                      .remoteStreams()
                      .doNotHaveJobType(jobType));
    }
  }

  @Test
  void shouldNotRemoveOtherStreamOnClientCancel() {
    // given - two logically equivalent clients
    final var stream =
        client.newStreamJobsCommand().jobType(jobType).consumer(ignored -> {}).send();
    client.newStreamJobsCommand().jobType(jobType).consumer(ignored -> {}).send();

    // when - two clients are registered on the gateway, and one of them is closed
    Awaitility.await("until gateway streams are fully connected")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveConnectedTo(2, jobType, 0, 1));
    stream.cancel(true);

    // then - the remaining gateway stream is present, and it's still connected to the brokers
    Awaitility.await("until gateway has only one stream")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveConnectedTo(1, jobType, 0, 1));
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var actuator = brokerActuator(nodeId);
      JobStreamActuatorAssert.assertThat(actuator).remoteStreams().haveJobType(1, jobType);
    }
  }

  @Test
  void shouldRemoveAllStreamsOnGatewayShutdown() {
    // given - two logically different clients
    client.newStreamJobsCommand().jobType(jobType).consumer(ignored -> {}).send();
    client
        .newStreamJobsCommand()
        .jobType(jobType)
        .consumer(ignored -> {})
        .fetchVariables("foo", "bar")
        .send();
    Awaitility.await("until gateway streams are fully connected")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(JobStreamActuator.of(gateway))
                    .clientStreams()
                    .haveConnectedTo(2, jobType, 0, 1));

    // when
    cluster.availableGateway().stop().start().await(TestHealthProbe.READY);
    cluster.awaitCompleteTopology();

    // then - no streams will be registered on any broker
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var actuator = brokerActuator(nodeId);
      Awaitility.await("until no stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(actuator)
                      .remoteStreams()
                      .doNotHaveJobType(jobType));
    }
  }

  private JobStreamActuator brokerActuator(final int nodeId) {
    final var brokerId = MemberId.from(String.valueOf(nodeId));
    final var broker = cluster.brokers().get(brokerId);
    return JobStreamActuator.of(broker);
  }
}
