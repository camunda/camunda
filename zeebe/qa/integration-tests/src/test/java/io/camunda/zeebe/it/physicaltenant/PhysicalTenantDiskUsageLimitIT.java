/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status.Code;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

@ZeebeIntegration
final class PhysicalTenantDiskUsageLimitIT {

  private static final String LIMITED_TENANT = "limited";
  private static final DataSize SEGMENT_SIZE = DataSize.ofMegabytes(4);
  private static final DataSize PROCESSING_LIMIT = DataSize.ofMegabytes(24);
  private static final DataSize REPLICATION_LIMIT = DataSize.ofMegabytes(32);
  private static final String PAYLOAD = "x".repeat(256 * 1024);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(LIMITED_TENANT, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              // with few, large entries the default would keep every segment behind a snapshot
              .withClusterConfig(
                  cluster -> cluster.getRaft().setPreferSnapshotReplicationThreshold(1))
              .withDataConfig(
                  data -> {
                    data.getPrimaryStorage().getLogStream().setLogSegmentSize(SEGMENT_SIZE);
                    data.getPrimaryStorage()
                        .getDisk()
                        .setMonitoringInterval(Duration.ofMillis(100));
                  })
              .withPtConfig(
                  LIMITED_TENANT,
                  camunda -> {
                    final var usedSpace =
                        camunda.getData().getPrimaryStorage().getDisk().getUsedSpace();
                    usedSpace.setProcessing(PROCESSING_LIMIT);
                    usedSpace.setReplication(REPLICATION_LIMIT);
                  }));

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient limitedClient;

  @BeforeEach
  void beforeEach() {
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    limitedClient = TENANTS.newClientBuilder(broker, LIMITED_TENANT).build();
    awaitWritesAccepted(defaultClient);
    awaitWritesAccepted(limitedClient);
  }

  @Test
  void shouldRejectCommandsOnlyForTenantAboveItsDiskLimit() {
    // given
    final var processId = deployStraightThroughProcess(defaultClient);

    // when
    fillUntilRejected(limitedClient);

    // then
    assertRejectedForOutOfDiskSpace(limitedClient);
    for (int i = 0; i < 3; i++) {
      final var result =
          defaultClient
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .variables(Map.of("payload", PAYLOAD))
              .withResult()
              .send()
              .join();
      assertThat(result.getProcessInstanceKey()).isPositive();
    }
    assertThatCode(() -> publishLargeMessage(defaultClient)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptCommandsAgainOnceTenantDataIsCompacted() {
    // given
    fillUntilRejected(limitedClient);

    // when
    PartitionsActuator.of(broker).takeSnapshot(LIMITED_TENANT);

    // then
    awaitWritesAccepted(limitedClient);
    final var processId = deployStraightThroughProcess(limitedClient);
    final var result =
        limitedClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .withResult()
            .send()
            .join();
    assertThat(result.getProcessInstanceKey()).isPositive();
  }

  private static String deployStraightThroughProcess(final CamundaClient client) {
    final var processId = "process-" + UUID.randomUUID();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn")
        .send()
        .join();
    return processId;
  }

  private static void fillUntilRejected(final CamundaClient client) {
    await("tenant is rejected once its data exceeds its disk limit")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(10))
        .untilAsserted(() -> assertRejectedForOutOfDiskSpace(client));
  }

  private static void awaitWritesAccepted(final CamundaClient client) {
    await("tenant accepts writes")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThatCode(() -> publishLargeMessage(client)).doesNotThrowAnyException());
  }

  private static void assertRejectedForOutOfDiskSpace(final CamundaClient client) {
    assertThatThrownBy(() -> publishLargeMessage(client))
        .isInstanceOf(ClientStatusException.class)
        .hasStackTraceContaining("Broker is out of disk space")
        .satisfies(
            t ->
                assertThat(((ClientStatusException) t).getStatusCode())
                    .isEqualTo(Code.RESOURCE_EXHAUSTED));
  }

  private static void publishLargeMessage(final CamundaClient client) {
    client
        .newPublishMessageCommand()
        .messageName("fill")
        .correlationKey(UUID.randomUUID().toString())
        .variables(Map.of("payload", PAYLOAD))
        .send()
        .join();
  }
}
