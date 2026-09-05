/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cluster-wide trigger against a cluster whose physical tenants disagree on where backup ids
 * come from: tenantA takes continuous backups and therefore generates its own ids, while the
 * default tenant is driven manually and requires one.
 *
 * <p>A separate broker from {@link ClusterRuntimeBackupIT} because the backup-id mode is a
 * deployment-time choice — which is exactly what makes this worth a full-stack test: only running
 * the real configuration proves that the per-tenant backup config reaches the cluster-wide trigger,
 * and that a mixed cluster is rejected outright rather than half triggered (ADR 003, Consequences).
 */
@Timeout(180)
@ZeebeIntegration
final class ClusterRuntimeBackupIdModeIT {

  private static final String TENANT_A = "tenanta";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @TempDir private static Path defaultBackupDir;
  @TempDir private static Path tenantABackupDir;

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      // Unauthenticated: this test is about the backup-id modes, not the cluster-admin chain, which
      // ClusterAdminBasicAuthenticationIT covers against a real secondary storage.
      new TestStandaloneBroker().withUnauthenticatedAccess();

  private static URI clusterBackupsUri;

  @BeforeAll
  static void startBrokerWithTenantsInDifferentBackupIdModes() {
    PhysicalTenantsITHelper.builder()
        .withTenant(DEFAULT_TENANT_ID, Storage.none())
        .withTenant(TENANT_A, Storage.none())
        .build()
        .configure(BROKER);
    // A physical tenant's config starts from the global one, so `continuous` has to be set on the
    // tenant that should generate its ids -- setting it globally would put both tenants in that
    // mode
    // and there would be nothing mixed about the cluster.
    BROKER
        .withDataConfig(
            data -> filesystemStore(data.getPrimaryStorage().getBackup(), defaultBackupDir))
        .withPtConfig(
            TENANT_A,
            camunda -> {
              final var backup = camunda.getData().getPrimaryStorage().getBackup();
              filesystemStore(backup, tenantABackupDir);
              backup.setContinuous(true);
            });
    BROKER.start().await(TestHealthProbe.READY);

    clusterBackupsUri =
        URI.create(
            BROKER.restAddress().toString().replaceAll("/+$", "") + "/cluster/v2/backups/runtime");
  }

  private static void filesystemStore(final PrimaryStorageBackup backup, final Path basePath) {
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(basePath.toString());
  }

  @Test
  void shouldRejectAnExplicitBackupIdNamingTheTenantThatGeneratesIds() throws Exception {
    // when
    final var taken = takeClusterBackup("{\"backupId\": 42}");

    // then nothing was triggered anywhere, and the operator is told which tenant is in the way
    assertThat(taken.statusCode()).isEqualTo(400);
    assertThat(JSON.readTree(taken.body()).get("detail").asText()).contains(TENANT_A);
  }

  @Test
  void shouldRejectAMissingBackupIdNamingTheTenantThatRequiresOne() throws Exception {
    // when
    final var taken = takeClusterBackup(null);

    // then
    assertThat(taken.statusCode()).isEqualTo(400);
    assertThat(JSON.readTree(taken.body()).get("detail").asText()).contains(DEFAULT_TENANT_ID);
  }

  /**
   * The escape hatch a mixed cluster is left with: each tenant can still be driven on its own
   * through the cluster-admin API, in the mode that tenant is configured for.
   */
  @Test
  void shouldAcceptAnExplicitBackupIdWhenNarrowedToTheManuallyDrivenTenant() throws Exception {
    // when
    final var request =
        HttpRequest.newBuilder(
                URI.create(clusterBackupsUri + "?physicalTenantId=" + DEFAULT_TENANT_ID))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{\"backupId\": 42}"))
            .build();
    final var taken = HTTP.send(request, BodyHandlers.ofString());

    // then
    assertThat(taken.statusCode()).isEqualTo(202);
    assertThat(JSON.readTree(taken.body()).get("physicalTenants"))
        .singleElement()
        .satisfies(
            outcome -> {
              assertThat(outcome.get("physicalTenantId").asText()).isEqualTo(DEFAULT_TENANT_ID);
              assertThat(outcome.get("outcome").asText()).isEqualTo("TRIGGERED");
              assertThat(outcome.get("backupId").asLong()).isEqualTo(42L);
            });
  }

  private static HttpResponse<String> takeClusterBackup(final String body)
      throws IOException, InterruptedException {
    final var builder = HttpRequest.newBuilder(clusterBackupsUri);
    if (body == null) {
      builder.POST(BodyPublishers.noBody());
    } else {
      builder.header("Content-Type", "application/json").POST(BodyPublishers.ofString(body));
    }
    return HTTP.send(builder.build(), BodyHandlers.ofString());
  }
}
