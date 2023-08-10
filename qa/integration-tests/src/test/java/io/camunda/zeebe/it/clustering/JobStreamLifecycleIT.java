/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.qa.util.jobstream.JobStreamClientAssert;
import io.camunda.zeebe.qa.util.jobstream.JobStreamServiceAssert;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.assertj.core.condition.AllOf;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class JobStreamLifecycleIT {
  @SuppressWarnings("JUnitMalformedDeclaration")
  @RegisterExtension
  private static final ClusteringRuleExtension CLUSTER = new ClusteringRuleExtension(1, 2, 2);

  private static GrpcClientRule client;

  private final String jobType = Strings.newRandomValidBpmnId();

  @BeforeAll
  static void beforeAll() {
    client = new GrpcClientRule(CLUSTER.getClient());
  }

  @Test
  void shouldRegisterStream() {
    // given
    final var command =
        client
            .getClient()
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
                JobStreamClientAssert.assertThat(CLUSTER.gatewayJobStreamClient())
                    .hasStreamMatchingCount(
                        1,
                        AllOf.allOf(
                            JobStreamClientAssert.hasStreamType(jobType),
                            JobStreamClientAssert.hasWorker("worker"),
                            JobStreamClientAssert.hasTimeout(500),
                            JobStreamClientAssert.hasFetchVariables("foo", "bar"))));
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var service = CLUSTER.getBrokerBridge(nodeId).getJobStreamService().orElseThrow();
      Awaitility.await("until stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () ->
                  JobStreamServiceAssert.assertThat(service)
                      .hasStreamMatchingCount(
                          1,
                          AllOf.allOf(
                              JobStreamServiceAssert.hasStreamType(jobType),
                              JobStreamServiceAssert.hasWorker("worker"),
                              JobStreamServiceAssert.hasTimeout(500),
                              JobStreamServiceAssert.hasFetchVariables("foo", "bar"))));
    }
  }

  @Test
  void shouldRegisterStreamsWithDifferentProperties() {
    // given - two logically DIFFERENT streams
    final var commandA =
        client
            .getClient()
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .fetchVariables("foo", "bar")
            .timeout(Duration.ofMillis(500))
            .workerName("commandA");
    final var commandB =
        client
            .getClient()
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
                JobStreamClientAssert.assertThat(CLUSTER.gatewayJobStreamClient())
                    .hasStreamWithType(2, jobType));

    // then - the streams are not aggregated on the broker side, as they are logically different
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var service = CLUSTER.getBrokerBridge(nodeId).getJobStreamService().orElseThrow();
      Awaitility.await("until stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () -> JobStreamServiceAssert.assertThat(service).hasStreamWithType(2, jobType));
    }
  }

  @Test
  void shouldAggregateStreams() {
    // given - two logically equivalent streams
    final var commandA =
        client
            .getClient()
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .fetchVariables("foo", "bar")
            .timeout(Duration.ofMillis(500))
            .workerName("command");
    final var commandB =
        client
            .getClient()
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
                JobStreamClientAssert.assertThat(CLUSTER.gatewayJobStreamClient())
                    .hasStreamWithType(2, jobType));

    // then - only one stream is registered on each broker as it is aggregated per gateway
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var service = CLUSTER.getBrokerBridge(nodeId).getJobStreamService().orElseThrow();
      Awaitility.await("until stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () -> JobStreamServiceAssert.assertThat(service).hasStreamWithType(1, jobType, 2));
    }
  }

  @Test
  void shouldRemoveStreamOnClientCancel() {
    // given - one stream is registered on the gateway
    final var stream =
        client.getClient().newStreamJobsCommand().jobType(jobType).consumer(ignored -> {}).send();
    Awaitility.await("until stream is fully registered")
        .untilAsserted(
            () ->
                JobStreamClientAssert.assertThat(CLUSTER.gatewayJobStreamClient())
                    .hasStreamWithType(1, jobType, 0, 1));

    // when - that stream is cancelled
    stream.cancel(true);

    // then - it is removed everywhere, as it was the last client for this logical stream
    Awaitility.await("until no gateway streams are registered")
        .untilAsserted(
            () ->
                JobStreamClientAssert.assertThat(CLUSTER.gatewayJobStreamClient())
                    .doesNotHaveStreamWithType(jobType));
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var service = CLUSTER.getBrokerBridge(nodeId).getJobStreamService().orElseThrow();
      Awaitility.await("until no stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () -> JobStreamServiceAssert.assertThat(service).doesNotHaveStreamWithType(jobType));
    }
  }

  @Test
  void shouldNotRemoveOtherStreamOnClientCancel() {
    // given - two logically equivalent clients
    final var stream =
        client.getClient().newStreamJobsCommand().jobType(jobType).consumer(ignored -> {}).send();
    client.getClient().newStreamJobsCommand().jobType(jobType).consumer(ignored -> {}).send();

    // when - two clients are registered on the gateway, and one of them is closed
    Awaitility.await("until gateway streams are fully connected")
        .untilAsserted(
            () ->
                JobStreamClientAssert.assertThat(CLUSTER.gatewayJobStreamClient())
                    .hasStreamWithType(2, jobType, 0, 1));
    stream.cancel(true);

    // then - the remaining gateway stream is present, and it's still connected to the brokers
    Awaitility.await("until gateway has only one stream")
        .untilAsserted(
            () ->
                JobStreamClientAssert.assertThat(CLUSTER.gatewayJobStreamClient())
                    .hasStreamWithType(1, jobType, 0, 1));
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var service = CLUSTER.getBrokerBridge(nodeId).getJobStreamService().orElseThrow();
      JobStreamServiceAssert.assertThat(service).hasStreamWithType(1, jobType);
    }
  }

  @Test
  void shouldRemoveAllStreamsOnGatewayShutdown() {
    // given - two logically different clients
    client.getClient().newStreamJobsCommand().jobType(jobType).consumer(ignored -> {}).send();
    client
        .getClient()
        .newStreamJobsCommand()
        .jobType(jobType)
        .consumer(ignored -> {})
        .fetchVariables("foo", "bar")
        .send();
    Awaitility.await("until gateway streams are fully connected")
        .untilAsserted(
            () ->
                JobStreamClientAssert.assertThat(CLUSTER.gatewayJobStreamClient())
                    .hasStreamWithType(2, jobType, 0, 1));

    // when
    CLUSTER.restartGateway();

    // then - no streams will be registered on any broker
    for (int nodeId = 0; nodeId < 2; nodeId++) {
      final var service = CLUSTER.getBrokerBridge(nodeId).getJobStreamService().orElseThrow();
      Awaitility.await("until no stream is registered on broker '%d'".formatted(nodeId))
          .untilAsserted(
              () -> JobStreamServiceAssert.assertThat(service).doesNotHaveStreamWithType(jobType));
    }
  }
}
