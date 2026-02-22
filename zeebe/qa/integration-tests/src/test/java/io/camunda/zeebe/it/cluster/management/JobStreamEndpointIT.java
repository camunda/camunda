/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert.RemoteJobStreamsAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.agrona.CloseHelper;
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

  @Test
  void shouldListTenantFilterOnRemoteStreams() {
    // given — two workers with different tenant filters, each with a unique job type so
    // haveExactlyAll(1, ...) assertions are independent of each other
    final var providedJobType = Strings.newRandomValidBpmnId();
    final var assignedJobType = Strings.newRandomValidBpmnId();

    final JobWorker providedWorker =
        client
            .newWorker()
            .jobType(providedJobType)
            .handler((c, j) -> {})
            .name("providedWorker")
            .timeout(Duration.ofMillis(500))
            .tenantFilter(TenantFilter.PROVIDED)
            .streamEnabled(true)
            .open();
    final JobWorker assignedWorker =
        client
            .newWorker()
            .jobType(assignedJobType)
            .handler((c, j) -> {})
            .name("assignedWorker")
            .timeout(Duration.ofMillis(500))
            .tenantFilter(TenantFilter.ASSIGNED)
            .streamEnabled(true)
            .open();

    // then — the broker's remote streams expose the correct tenantFilter for each worker
    final var brokerActuator = JobStreamActuator.of(cluster.brokers().get(MemberId.from("0")));
    Awaitility.await("until PROVIDED stream is registered on broker")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(brokerActuator)
                    .remoteStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(providedJobType),
                        RemoteJobStreamsAssert.hasWorker("providedWorker"),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED)));
    Awaitility.await("until ASSIGNED stream is registered on broker")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(brokerActuator)
                    .remoteStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(assignedJobType),
                        RemoteJobStreamsAssert.hasWorker("assignedWorker"),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.ASSIGNED)));

    CloseHelper.quietCloseAll(providedWorker, assignedWorker);
  }

  @Test
  void shouldListTenantFilterOnClientStreams() {
    // given — two workers with different tenant filters, each with a unique job type
    final var providedJobType = Strings.newRandomValidBpmnId();
    final var assignedJobType = Strings.newRandomValidBpmnId();

    final JobWorker providedWorker =
        client
            .newWorker()
            .jobType(providedJobType)
            .handler((c, j) -> {})
            .name("providedWorker")
            .timeout(Duration.ofMillis(500))
            .tenantFilter(TenantFilter.PROVIDED)
            .streamEnabled(true)
            .open();
    final JobWorker assignedWorker =
        client
            .newWorker()
            .jobType(assignedJobType)
            .handler((c, j) -> {})
            .name("assignedWorker")
            .timeout(Duration.ofMillis(500))
            .tenantFilter(TenantFilter.ASSIGNED)
            .streamEnabled(true)
            .open();

    // then — the gateway's client streams expose the correct tenantFilter for each worker
    final var gatewayActuator = JobStreamActuator.of(gateway);
    Awaitility.await("until PROVIDED stream is registered on gateway")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(gatewayActuator)
                    .clientStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(providedJobType),
                        RemoteJobStreamsAssert.hasWorker("providedWorker"),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED)));
    Awaitility.await("until ASSIGNED stream is registered on gateway")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(gatewayActuator)
                    .clientStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(assignedJobType),
                        RemoteJobStreamsAssert.hasWorker("assignedWorker"),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.ASSIGNED)));

    CloseHelper.quietCloseAll(providedWorker, assignedWorker);
  }

  @Test
  void shouldReportDefaultTenantFilterAsProvided() {
    // given — a stream with no explicit tenant filter (default should be PROVIDED)
    final var jobType = Strings.newRandomValidBpmnId();
    final var command =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .workerName("default");

    // when
    command.send();

    // then — the actuator should report the tenant filter as PROVIDED
    final var gatewayActuator = JobStreamActuator.of(gateway);
    Awaitility.await("until stream is registered on gateway")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(gatewayActuator)
                    .clientStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(jobType),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED)));
    final var brokerActuator = JobStreamActuator.of(cluster.brokers().get(MemberId.from("0")));
    Awaitility.await("until stream is registered on broker")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(brokerActuator)
                    .remoteStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(jobType),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED)));
  }

  @Test
  void shouldReportProvidedTenantFilterOnActuator() {
    // given — a stream with an explicit PROVIDED tenant filter
    final var jobType = Strings.newRandomValidBpmnId();
    final var command =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .workerName("provided")
            .tenantFilter(TenantFilter.PROVIDED);

    // when
    command.send();

    // then
    final var gatewayActuator = JobStreamActuator.of(gateway);
    Awaitility.await("until stream is registered on gateway")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(gatewayActuator)
                    .clientStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(jobType),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED)));
    final var brokerActuator = JobStreamActuator.of(cluster.brokers().get(MemberId.from("0")));
    Awaitility.await("until stream is registered on broker")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(brokerActuator)
                    .remoteStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(jobType),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED)));
  }

  @Test
  void shouldReportAssignedTenantFilterOnActuator() {
    // given — a stream with ASSIGNED tenant filter
    final var jobType = Strings.newRandomValidBpmnId();
    final var command =
        client
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(ignored -> {})
            .workerName("assigned")
            .tenantFilter(TenantFilter.ASSIGNED);

    // when
    command.send();

    // then
    final var gatewayActuator = JobStreamActuator.of(gateway);
    Awaitility.await("until stream is registered on gateway")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(gatewayActuator)
                    .clientStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(jobType),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.ASSIGNED)));
    final var brokerActuator = JobStreamActuator.of(cluster.brokers().get(MemberId.from("0")));
    Awaitility.await("until stream is registered on broker")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(brokerActuator)
                    .remoteStreams()
                    .haveExactlyAll(
                        1,
                        RemoteJobStreamsAssert.hasJobType(jobType),
                        RemoteJobStreamsAssert.hasTenantFilter(
                            io.camunda.zeebe.protocol.record.value.TenantFilter.ASSIGNED)));
  }
}
