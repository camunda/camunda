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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status.Code;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Changes the disk limit of a physical tenant through the management API while the broker runs, and
 * checks that the broker enforces the new limit: a lowered limit pauses only that tenant, and a
 * raised one lets it accept commands again without compacting its data.
 */
@ZeebeIntegration
final class PhysicalTenantDiskLimitUpdateIT {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final DataSize SEGMENT_SIZE = DataSize.ofMegabytes(4);
  private static final String PAYLOAD = "x".repeat(256 * 1024);
  private static final int FILL_MESSAGES = 160;
  private static final String PROCESSING = "data.primary-storage.disk.used-space.processing";
  private static final String REPLICATION = "data.primary-storage.disk.used-space.replication";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withDataConfig(
                  data -> {
                    data.getPrimaryStorage().getLogStream().setLogSegmentSize(SEGMENT_SIZE);
                    data.getPrimaryStorage()
                        .getDisk()
                        .setMonitoringInterval(Duration.ofMillis(100));
                  }));

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantClient;

  @Test
  void shouldEnforceADiskLimitChangedAtRuntime() {
    // given
    final var tenantId = "dl" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    assertThat(send("POST", tenantId, configuration("512MB", "640MB")).statusCode()).isEqualTo(202);
    awaitRolledOut(tenantId, 1);
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    tenantClient = TENANTS.newClientBuilder(broker, tenantId).preferRestOverGrpc(false).build();
    awaitWritesAccepted(tenantClient);
    for (int i = 0; i < FILL_MESSAGES; i++) {
      publishLargeMessage(tenantClient);
    }

    // when
    assertThat(send("PUT", tenantId, configuration("24MB", "32MB")).statusCode()).isEqualTo(202);
    awaitRolledOut(tenantId, 2);

    // then
    assertThat(processingLimitOf(tenantId)).isEqualTo(DataSize.ofMegabytes(24));
    await("the tenant is paused by its lowered disk limit")
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(50))
        .ignoreExceptionsMatching(e -> !(e instanceof AssertionError))
        .untilAsserted(() -> assertRejectedForOutOfDiskSpace(tenantClient));
    assertThatCode(() -> publishLargeMessage(defaultClient)).doesNotThrowAnyException();

    // when
    assertThat(send("PUT", tenantId, configuration("512MB", "640MB")).statusCode()).isEqualTo(202);
    awaitRolledOut(tenantId, 3);

    // then
    awaitWritesAccepted(tenantClient);
  }

  private DataSize processingLimitOf(final String tenantId) {
    return broker
        .bean(PhysicalTenantLifecycleService.class)
        .configuration(tenantId)
        .orElseThrow()
        .getData()
        .getPrimaryStorage()
        .getDisk()
        .getUsedSpace()
        .getProcessing();
  }

  private Map<String, String> configuration(final String processing, final String replication) {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    configuration.put(PROCESSING, processing);
    configuration.put(REPLICATION, replication);
    if (broker
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    return configuration;
  }

  private void awaitRolledOut(final String tenantId, final long version) {
    await("physical tenant '%s' runs version %d".formatted(tenantId, version))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = JSON.readTree(send("GET", tenantId, null).body());
              assertThat(status.get("version").asLong()).isEqualTo(version);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
            });
  }

  private static void awaitWritesAccepted(final CamundaClient client) {
    await("tenant accepts writes")
        .atMost(TIMEOUT)
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

  private HttpResponse<String> send(
      final String method, final String tenantId, final Map<String, String> body) {
    try (final var http = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(broker.actuatorUri("physicalTenants", tenantId))
              .header("Content-Type", "application/json")
              .method(
                  method,
                  body == null
                      ? BodyPublishers.noBody()
                      : BodyPublishers.ofString(JSON.writeValueAsString(body)))
              .build();
      return http.send(request, BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
