/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that {@code /v2/backups/runtime} REST endpoints act only on the addressed physical
 * tenant's partitions: a backup triggered on one tenant is invisible (and inactionable) through
 * another tenant's REST path, mirroring the behavior of the {@code backupRuntime} actuator's {@code
 * ?physicalTenant=} scoping.
 *
 * <p>No secondary storage is required for these assertions, so both tenants use {@link
 * Storage#none()} and are distinguished purely by their own filesystem backup store directory.
 */
@Timeout(120)
@ZeebeIntegration
final class PhysicalTenantBackupIT {

  private static final String TENANT_A = "tenanta";
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir private static Path defaultBackupDir;
  @TempDir private static Path tenantABackupDir;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
          .withDataConfig(
              data -> {
                final var backup = data.getPrimaryStorage().getBackup();
                backup.setStore(BackupStoreType.FILESYSTEM);
                backup.getFilesystem().setBasePath(defaultBackupDir.toString());
              })
          .withPtConfig(
              TENANT_A,
              camunda -> {
                final var backup = camunda.getData().getPrimaryStorage().getBackup();
                backup.setStore(BackupStoreType.FILESYSTEM);
                backup.getFilesystem().setBasePath(tenantABackupDir.toString());
              });

  private URI defaultBackupsUri;
  private URI tenantABackupsUri;

  @BeforeEach
  void setUp() {
    final var base = broker.restAddress().toString().replaceAll("/+$", "");
    defaultBackupsUri = URI.create(base + "/v2/backups/runtime");
    tenantABackupsUri = URI.create(base + "/physical-tenants/" + TENANT_A + "/v2/backups/runtime");
  }

  @Test
  void shouldScopeRuntimeBackupsToTheAddressedPhysicalTenant() throws Exception {
    // given a backup taken on tenantA
    final long backupId = System.currentTimeMillis();
    final var takeResponse = takeBackup(tenantABackupsUri, backupId);
    assertThat(takeResponse.statusCode()).isEqualTo(202);

    // then it eventually completes when queried through tenantA's own path ...
    Awaitility.await("tenantA's backup completes")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var status = getBackupStatus(tenantABackupsUri, backupId);
              assertThat(status.statusCode()).isEqualTo(200);
              assertThat(MAPPER.readTree(status.body()).path("state").asText())
                  .isEqualTo("COMPLETED");
            });

    // ... but is invisible through the default tenant's path
    final var statusThroughDefault = getBackupStatus(defaultBackupsUri, backupId);
    assertThat(statusThroughDefault.statusCode()).isEqualTo(404);

    // and listing on the default tenant does not include tenantA's backup
    final var listOnDefault = listBackups(defaultBackupsUri);
    assertThat(listOnDefault.statusCode()).isEqualTo(200);
    assertThat(MAPPER.readTree(listOnDefault.body())).isEmpty();

    // while listing on tenantA does
    final var listOnTenantA = listBackups(tenantABackupsUri);
    assertThat(listOnTenantA.statusCode()).isEqualTo(200);
    assertThat(MAPPER.readTree(listOnTenantA.body())).hasSize(1);

    // deleting through tenantA's path removes it, and it is then gone from tenantA too
    final var deleteResponse = deleteBackup(tenantABackupsUri, backupId);
    assertThat(deleteResponse.statusCode()).isEqualTo(204);
    Awaitility.await("tenantA's backup is deleted")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(getBackupStatus(tenantABackupsUri, backupId).statusCode())
                    .isEqualTo(404));
  }

  private static HttpResponse<String> takeBackup(final URI baseUri, final long backupId)
      throws IOException, InterruptedException {
    final var body = "{\"backupId\": " + backupId + "}";
    final var request =
        HttpRequest.newBuilder(baseUri)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build();
    return HTTP.send(request, BodyHandlers.ofString());
  }

  private static HttpResponse<String> getBackupStatus(final URI baseUri, final long backupId)
      throws IOException, InterruptedException {
    final var request = HttpRequest.newBuilder(URI.create(baseUri + "/" + backupId)).GET().build();
    return HTTP.send(request, BodyHandlers.ofString());
  }

  private static HttpResponse<String> listBackups(final URI baseUri)
      throws IOException, InterruptedException {
    final var request = HttpRequest.newBuilder(baseUri).GET().build();
    return HTTP.send(request, BodyHandlers.ofString());
  }

  private static HttpResponse<String> deleteBackup(final URI baseUri, final long backupId)
      throws IOException, InterruptedException {
    final var request =
        HttpRequest.newBuilder(URI.create(baseUri + "/" + backupId)).DELETE().build();
    return HTTP.send(request, BodyHandlers.ofString());
  }
}
