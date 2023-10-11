/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.jobstream.AbstractJobStreamsAssert;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@AutoCloseResources
@ZeebeIntegration
final class JobStreamLifecycleIT {
  @TestZeebe
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withReplicationFactor(2)
          .withBrokersCount(2)
          .withGatewaysCount(1)
          .withEmbeddedGateway(false)
          .build();

  @AutoCloseResource private static ZeebeClient client;

  private final String jobType = Strings.newRandomValidBpmnId();

  @BeforeAll
  static void beforeAll() {
    client = CLUSTER.newClientBuilder().build();
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
                JobStreamActuatorAssert.assertThat(gatewayActuator())
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
                JobStreamActuatorAssert.assertThat(gatewayActuator())
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
                JobStreamActuatorAssert.assertThat(gatewayActuator())
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
                      .haveConsumerCount(1, jobType, 2));
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
                JobStreamActuatorAssert.assertThat(gatewayActuator())
                    .clientStreams()
                    .haveConnectedTo(1, jobType, 0, 1));

    // when - that stream is cancelled
    stream.cancel(true);

    // then - it is removed everywhere, as it was the last client for this logical stream
    Awaitility.await("until no gateway streams are registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(gatewayActuator())
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
                JobStreamActuatorAssert.assertThat(gatewayActuator())
                    .clientStreams()
                    .haveConnectedTo(2, jobType, 0, 1));
    stream.cancel(true);

    // then - the remaining gateway stream is present, and it's still connected to the brokers
    Awaitility.await("until gateway has only one stream")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(gatewayActuator())
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
                JobStreamActuatorAssert.assertThat(gatewayActuator())
                    .clientStreams()
                    .haveConnectedTo(2, jobType, 0, 1));

    // when
    CLUSTER.availableGateway().stop().start().await(TestHealthProbe.READY);
    CLUSTER.awaitCompleteTopology();

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

  private JobStreamActuator gatewayActuator() {
    return JobStreamActuator.of(CLUSTER.availableGateway());
  }

  private JobStreamActuator brokerActuator(final int nodeId) {
    final var brokerId = MemberId.from(String.valueOf(nodeId));
    final var broker = CLUSTER.brokers().get(brokerId);
    return JobStreamActuator.of(broker);
  }
}
