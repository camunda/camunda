/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupConfig;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Backups of physical tenants created through the {@code physicalTenants} actuator while the broker
 * runs: they are taken, listed and queried through the {@code backupRuntime} actuator like those of
 * a tenant of the static configuration, their scheduled backups follow a change of their schedule,
 * and stop when the tenant is removed.
 */
@ZeebeIntegration
final class PhysicalTenantRuntimeBackupIT {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration TIMEOUT = Duration.ofSeconds(90);
  private static final String SCHEDULER_METER =
      "camunda.checkpoint.scheduler.next.checkpoint.time.millis";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .build();

  private static @TempDir Path backupRoot;

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withDataConfig(
                  data -> {
                    final var backup = data.getPrimaryStorage().getBackup();
                    backup.setStore(BackupStoreType.FILESYSTEM);
                    backup
                        .getFilesystem()
                        .setBasePath(backupRoot.resolve("default").toAbsolutePath().toString());
                  }));

  @Test
  void shouldTakeListAndQueryBackupsOfATenantCreatedAtRuntime() throws Exception {
    // given
    final var tenantId = uniqueTenantId();
    assertThat(sendTenant("POST", tenantId, configuration(tenantId, null)).statusCode())
        .isEqualTo(202);
    awaitRolledOut(tenantId, 1);
    final long backupId = 7;

    // when
    final var taken =
        sendBackup(
            "POST",
            "",
            JSON.writeValueAsString(Map.of("backupId", backupId, "physicalTenant", tenantId)));

    // then
    assertThat(taken.statusCode()).describedAs(taken.body()).isEqualTo(202);
    await("backup %d of %s completes".formatted(backupId, tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(backupStatus(tenantId, backupId).path("state").asText())
                    .isEqualTo("COMPLETED"));
    final var listed = sendBackup("GET", "?physicalTenant=" + tenantId, null);
    assertThat(listed.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(listed.body()).findValuesAsText("backupId"))
        .contains(String.valueOf(backupId));
    assertThat(completedBackupCount(backupLocation(tenantId))).isPositive();
  }

  @Test
  void shouldTakeScheduledBackupsOnceTheirScheduleIsAddedAtRuntime() {
    // given
    final var tenantId = uniqueTenantId();
    assertThat(sendTenant("POST", tenantId, configuration(tenantId, null)).statusCode())
        .isEqualTo(202);
    awaitRolledOut(tenantId, 1);
    assertThat(schedulerMeters(tenantId)).isZero();

    // when
    final var updated = sendTenant("PUT", tenantId, configuration(tenantId, "PT1S"));

    // then
    assertThat(updated.statusCode()).describedAs(updated.body()).isEqualTo(202);
    awaitRolledOut(tenantId, 2);
    await("scheduled backups of %s reach its store".formatted(tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> assertThat(completedBackupCount(backupLocation(tenantId))).isPositive());
    assertThat(schedulerMeters(tenantId)).isPositive();
  }

  @Test
  void shouldStopTheScheduledBackupsOfARemovedTenant() {
    // given
    final var tenantId = uniqueTenantId();
    assertThat(sendTenant("POST", tenantId, configuration(tenantId, "PT1S")).statusCode())
        .isEqualTo(202);
    awaitRolledOut(tenantId, 1);
    await("scheduled backups of %s reach its store".formatted(tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> assertThat(completedBackupCount(backupLocation(tenantId))).isPositive());
    assertThat(schedulerMeters(tenantId)).isPositive();

    // when
    assertThat(sendTenant("DELETE", tenantId, null).statusCode()).isEqualTo(202);

    // then
    await("%s is removed".formatted(tenantId))
        .atMost(TIMEOUT)
        .until(() -> lifecycle().configuration(tenantId).isEmpty());
    assertThat(schedulerMeters(tenantId)).isZero();
    final var count = completedBackupCount(backupLocation(tenantId));
    await("no backup of %s is taken after its removal".formatted(tenantId))
        .during(Duration.ofSeconds(4))
        .atMost(Duration.ofSeconds(10))
        .until(() -> completedBackupCount(backupLocation(tenantId)) == count);
  }

  @Test
  void shouldRejectAChangeOfTheBackupStoreLocationOfATenantCreatedAtRuntime() {
    // given
    final var tenantId = uniqueTenantId();
    assertThat(sendTenant("POST", tenantId, configuration(tenantId, null)).statusCode())
        .isEqualTo(202);
    awaitRolledOut(tenantId, 1);
    final var moved = configuration(tenantId, null);
    moved.put(
        "data.primary-storage.backup.filesystem.base-path",
        backupRoot.resolve(tenantId + "-moved").toString());

    // when
    final var response = sendTenant("PUT", tenantId, moved);

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("filesystem.base-path", "storage-location");
  }

  @Test
  void shouldRejectATenantCreatedAtRuntimeOnTheBackupLocationOfARunningTenant() {
    // given
    final var tenantId = uniqueTenantId();
    final var sharing = configuration(tenantId, null);
    sharing.put(
        "data.primary-storage.backup.filesystem.base-path",
        backupRoot.resolve("default").toAbsolutePath().toString());

    // when
    final var response = sendTenant("POST", tenantId, sharing);

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("share the same backup location");
  }

  @Test
  void shouldLetATenantCreatedAtRuntimeReuseTheBackupLocationOfARemovedTenant() {
    // given
    final var removedId = uniqueTenantId();
    assertThat(sendTenant("POST", removedId, configuration(removedId, null)).statusCode())
        .isEqualTo(202);
    awaitRolledOut(removedId, 1);
    assertThat(sendTenant("DELETE", removedId, null).statusCode()).isEqualTo(202);
    await("%s is removed".formatted(removedId))
        .atMost(TIMEOUT)
        .until(() -> lifecycle().configuration(removedId).isEmpty());
    final var tenantId = uniqueTenantId();
    final var reusing = configuration(tenantId, null);
    reusing.put(
        "data.primary-storage.backup.filesystem.base-path",
        backupLocation(removedId).toAbsolutePath().toString());

    // when
    final var response = sendTenant("POST", tenantId, reusing);

    // then
    assertThat(response.statusCode()).describedAs(response.body()).isEqualTo(202);
  }

  private long schedulerMeters(final String tenantId) {
    return broker
        .bean(MeterRegistry.class)
        .find(SCHEDULER_METER)
        .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), tenantId)
        .meters()
        .size();
  }

  private JsonNode backupStatus(final String tenantId, final long backupId) throws Exception {
    final var response = sendBackup("GET", "/" + backupId + "?physicalTenant=" + tenantId, null);
    assertThat(response.statusCode()).describedAs(response.body()).isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private PhysicalTenantLifecycleService lifecycle() {
    return broker.bean(PhysicalTenantLifecycleService.class);
  }

  private static Path backupLocation(final String tenantId) {
    return backupRoot.resolve(tenantId);
  }

  private static long completedBackupCount(final Path location) {
    final var store =
        FilesystemBackupStore.of(new FilesystemBackupConfig(location.toAbsolutePath().toString()));
    try {
      return store
          .list(
              new BackupIdentifierWildcardImpl(
                  Optional.empty(), Optional.empty(), CheckpointPattern.any()))
          .join()
          .stream()
          .filter(status -> status.statusCode() == BackupStatusCode.COMPLETED)
          .count();
    } finally {
      store.closeAsync().join();
    }
  }

  private Map<String, String> configuration(final String tenantId, final String schedule) {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    configuration.put("data.primary-storage.backup.store", "filesystem");
    configuration.put(
        "data.primary-storage.backup.filesystem.base-path",
        backupLocation(tenantId).toAbsolutePath().toString());
    if (schedule != null) {
      configuration.put("data.primary-storage.backup.schedule", schedule);
    }
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
              final var status = JSON.readTree(sendTenant("GET", tenantId, null).body());
              assertThat(status.get("version").asLong()).isEqualTo(version);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
            });
  }

  private HttpResponse<String> sendTenant(
      final String method, final String tenantId, final Map<String, String> body) {
    try {
      return send(
          method,
          broker.actuatorUri("physicalTenants", tenantId),
          body == null ? null : JSON.writeValueAsString(body));
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private HttpResponse<String> sendBackup(
      final String method, final String pathAndQuery, final String body) {
    return send(method, URI.create(broker.actuatorUri("backupRuntime") + pathAndQuery), body);
  }

  private static HttpResponse<String> send(final String method, final URI uri, final String body) {
    try (final var http = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .method(
                  method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
              .build();
      return http.send(request, BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String uniqueTenantId() {
    return "bk" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }
}
