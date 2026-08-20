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

import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cluster-wide runtime backup endpoints against two physical tenants, each with its own
 * filesystem backup store.
 *
 * <p>Exercises what a mocked-port test cannot: that one call really does take a backup on every
 * tenant's own partitions and store, that a backup only one tenant holds reads cluster-wide as
 * {@code INCOMPLETE} rather than as a failure, and that a partially-triggered request names the
 * tenants whose backups are running.
 *
 * <p>No secondary storage is needed for any of it, which is why these endpoints have no storage
 * gate — so both tenants use {@link Storage#none()}.
 */
@Timeout(240)
@ZeebeIntegration
final class ClusterRuntimeBackupIT {

  private static final String TENANT_A = "tenanta";

  private static final Duration BACKUP_TIMEOUT = Duration.ofSeconds(60);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  /**
   * Backup ids must ascend within a physical tenant, and a deleted backup does not release its id
   * because checkpoint ids stay monotonic. The tests share one broker, so each takes the next id
   * from here instead of a literal — that keeps them passing in whatever order JUnit runs them.
   */
  private static final AtomicLong NEXT_BACKUP_ID = new AtomicLong(500);

  @TempDir private static Path defaultBackupDir;
  @TempDir private static Path tenantABackupDir;

  // autoStart = false because the backup stores live in @TempDir directories, which JUnit injects
  // only after this field is initialized; the configuration is therefore applied in @BeforeAll.
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      // Unauthenticated: this test is about the fan-out contract, not the cluster-admin chain,
      // which
      // ClusterAdminBasicAuthenticationIT covers against a real secondary storage. It also lets the
      // test stage a single-tenant backup through that tenant's own /v2 endpoint.
      new TestStandaloneBroker().withUnauthenticatedAccess();

  private static URI clusterBackupsUri;
  private static URI tenantABackupsUri;

  @BeforeAll
  static void startBrokerWithATenantOwnedBackupStoreEach() {
    PhysicalTenantsITHelper.builder()
        .withTenant(DEFAULT_TENANT_ID, Storage.none())
        .withTenant(TENANT_A, Storage.none())
        .build()
        .configure(BROKER);
    BROKER
        .withDataConfig(
            data -> filesystemStore(data.getPrimaryStorage().getBackup(), defaultBackupDir))
        .withPtConfig(
            TENANT_A,
            camunda ->
                filesystemStore(
                    camunda.getData().getPrimaryStorage().getBackup(), tenantABackupDir));
    // autoStart is off, so readiness is awaited here rather than by the extension.
    BROKER.start().await(TestHealthProbe.READY);

    final var base = BROKER.restAddress().toString().replaceAll("/+$", "");
    clusterBackupsUri = URI.create(base + "/cluster/v2/backups/runtime");
    tenantABackupsUri = URI.create(base + "/physical-tenants/" + TENANT_A + "/v2/backups/runtime");
  }

  private static void filesystemStore(final PrimaryStorageBackup backup, final Path basePath) {
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(basePath.toString());
  }

  @Test
  void shouldTakeReadAndDeleteABackupOnEveryPhysicalTenant() throws Exception {
    // given
    final long backupId = NEXT_BACKUP_ID.incrementAndGet();

    // when
    final var taken = takeClusterBackup(backupId);

    // then every tenant reports the requested id, in tenant id order
    assertThat(taken.statusCode()).isEqualTo(202);
    final var outcomes = JSON.readTree(taken.body()).get("physicalTenants");
    assertThat(physicalTenantIds(outcomes)).containsExactly(DEFAULT_TENANT_ID, TENANT_A);
    assertThat(outcomes)
        .allSatisfy(
            outcome -> {
              assertThat(outcome.get("outcome").asText()).isEqualTo("TRIGGERED");
              assertThat(outcome.get("backupId").asLong()).isEqualTo(backupId);
            });

    // and the aggregated state eventually reports the whole cluster as backed up
    Awaitility.await("the cluster-wide backup completes on every physical tenant")
        .atMost(BACKUP_TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = getClusterBackup(backupId);
              assertThat(status.statusCode()).isEqualTo(200);
              final var body = JSON.readTree(status.body());
              assertThat(body.get("state").asText()).isEqualTo("COMPLETED");
              assertThat(body.get("physicalTenants"))
                  .allSatisfy(
                      tenant -> assertThat(tenant.get("state").asText()).isEqualTo("COMPLETED"));
            });

    // and the listing groups both tenants under that one id
    final var listed = listClusterBackups();
    assertThat(listed.statusCode()).isEqualTo(200);
    final var group = groupOf(JSON.readTree(listed.body()), backupId);
    assertThat(physicalTenantIds(group.get("physicalTenants")))
        .containsExactly(DEFAULT_TENANT_ID, TENANT_A);

    // when deleted cluster-wide
    assertThat(deleteClusterBackup(backupId).statusCode()).isEqualTo(204);

    // then it is gone from every physical tenant, including through the tenant's own path
    Awaitility.await("the backup is gone from every physical tenant")
        .atMost(BACKUP_TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(getClusterBackup(backupId).statusCode()).isEqualTo(404);
              assertThat(get(tenantABackupUri(backupId), null).statusCode()).isEqualTo(404);
            });
  }

  /**
   * A backup only one physical tenant holds is a supported outcome — a tenant's own operator can
   * take one, and so can a narrowed cluster-admin request — so reading it cluster-wide reports
   * every targeted tenant instead of 404, and says the set is not usable as a whole.
   */
  @Test
  void shouldReportABackupOnlyOnePhysicalTenantHoldsAsIncomplete() throws Exception {
    // given a backup staged through tenantA's own endpoint
    final long backupId = NEXT_BACKUP_ID.incrementAndGet();
    assertThat(takeTenantABackup(backupId).statusCode()).isEqualTo(202);

    // when - then
    Awaitility.await("the cluster-wide read reports the backup as incomplete")
        .atMost(BACKUP_TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = getClusterBackup(backupId);
              assertThat(status.statusCode()).isEqualTo(200);
              final var read = JSON.readTree(status.body());
              assertThat(read.get("state").asText()).isEqualTo("INCOMPLETE");
              assertThat(fieldOf(read.get("physicalTenants"), TENANT_A, "state"))
                  .isEqualTo("COMPLETED");
              assertThat(fieldOf(read.get("physicalTenants"), DEFAULT_TENANT_ID, "state"))
                  .isEqualTo("DOES_NOT_EXIST");
            });

    // and the listing says the same thing about it, rather than reporting it as complete because
    // only the tenant holding it was counted
    final var listed = listClusterBackups();
    assertThat(listed.statusCode()).isEqualTo(200);
    final var group = groupOf(JSON.readTree(listed.body()), backupId);
    assertThat(group.get("state").asText()).isEqualTo("INCOMPLETE");
    assertThat(physicalTenantIds(group.get("physicalTenants")))
        .containsExactly(DEFAULT_TENANT_ID, TENANT_A);
    assertThat(fieldOf(group.get("physicalTenants"), DEFAULT_TENANT_ID, "state"))
        .isEqualTo("DOES_NOT_EXIST");

    assertThat(deleteClusterBackup(backupId).statusCode()).isEqualTo(204);
  }

  /**
   * The guarantee of ADR 003 D4: a request only some tenants accepted answers with an error status,
   * and still names the tenants whose backups are now running so they can be cleaned up.
   */
  @Test
  void shouldNameTheTriggeredPhysicalTenantsWhenAnotherRejectsTheId() throws Exception {
    // given an id tenantA already holds
    final long backupId = NEXT_BACKUP_ID.incrementAndGet();
    assertThat(takeTenantABackup(backupId).statusCode()).isEqualTo(202);
    Awaitility.await("tenantA holds the id")
        .atMost(BACKUP_TIMEOUT)
        .untilAsserted(
            () -> assertThat(get(tenantABackupUri(backupId), null).statusCode()).isEqualTo(200));

    // when the same id is requested cluster-wide
    final var taken = takeClusterBackup(backupId);

    // then the request fails, but says which tenant is now running a backup under that id
    assertThat(taken.statusCode()).isNotEqualTo(202);
    final var outcomes = JSON.readTree(taken.body()).get("physicalTenants");
    assertThat(outcomes).isNotNull();
    assertThat(fieldOf(outcomes, DEFAULT_TENANT_ID, "outcome")).isEqualTo("TRIGGERED");
    assertThat(fieldOf(outcomes, TENANT_A, "outcome")).isEqualTo("FAILED");

    assertThat(deleteClusterBackup(backupId).statusCode()).isEqualTo(204);
  }

  @Test
  void shouldReportTheRuntimeStateOfEveryPhysicalTenant() throws Exception {
    // when
    final var state = get(uri("/state"), null);

    // then every tenant contributes its own checkpoint and backup state, folded into nothing
    assertThat(state.statusCode()).isEqualTo(200);
    final var tenants = JSON.readTree(state.body()).get("physicalTenants");
    assertThat(physicalTenantIds(tenants)).containsExactly(DEFAULT_TENANT_ID, TENANT_A);
    assertThat(tenants)
        .allSatisfy(tenant -> assertThat(tenant.get("state").get("ranges")).isNotNull());
  }

  @Test
  void shouldRejectAnUnknownPhysicalTenant() throws Exception {
    // when
    final var listed = get(uri("?physicalTenantId=nosuchtenant"), null);

    // then an unknown id is a request error, never a tenant that failed
    assertThat(listed.statusCode()).isEqualTo(404);
  }

  private static URI tenantABackupUri(final long backupId) {
    return URI.create(tenantABackupsUri + "/" + backupId);
  }

  private static URI uri(final String suffix) {
    return URI.create(clusterBackupsUri + suffix);
  }

  private static HttpResponse<String> takeClusterBackup(final long backupId)
      throws IOException, InterruptedException {
    final var request =
        HttpRequest.newBuilder(clusterBackupsUri)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{\"backupId\": " + backupId + "}"))
            .build();
    return HTTP.send(request, BodyHandlers.ofString());
  }

  private static HttpResponse<String> takeTenantABackup(final long backupId)
      throws IOException, InterruptedException {
    final var request =
        HttpRequest.newBuilder(tenantABackupsUri)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{\"backupId\": " + backupId + "}"))
            .build();
    return HTTP.send(request, BodyHandlers.ofString());
  }

  private static HttpResponse<String> getClusterBackup(final long backupId)
      throws IOException, InterruptedException {
    return get(uri("/" + backupId), null);
  }

  private static HttpResponse<String> listClusterBackups()
      throws IOException, InterruptedException {
    return get(clusterBackupsUri, null);
  }

  private static HttpResponse<String> deleteClusterBackup(final long backupId)
      throws IOException, InterruptedException {
    final var request = HttpRequest.newBuilder(uri("/" + backupId)).DELETE().build();
    return HTTP.send(request, BodyHandlers.ofString());
  }

  private static HttpResponse<String> get(final URI uri, final String authorizationHeader)
      throws IOException, InterruptedException {
    final var builder = HttpRequest.newBuilder(uri).GET();
    if (authorizationHeader != null) {
      builder.header("Authorization", authorizationHeader);
    }
    return HTTP.send(builder.build(), BodyHandlers.ofString());
  }

  private static List<String> physicalTenantIds(final JsonNode entries) {
    return StreamSupport.stream(entries.spliterator(), false)
        .map(entry -> entry.get("physicalTenantId").asText())
        .toList();
  }

  private static JsonNode groupOf(final JsonNode groups, final long backupId) {
    return StreamSupport.stream(groups.spliterator(), false)
        .filter(group -> group.get("backupId").asLong() == backupId)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("Expected backup '%d' to be listed".formatted(backupId)));
  }

  private static String fieldOf(
      final JsonNode entries, final String physicalTenantId, final String field) {
    return StreamSupport.stream(entries.spliterator(), false)
        .filter(entry -> entry.get("physicalTenantId").asText().equals(physicalTenantId))
        .map(entry -> entry.get(field).asText())
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Expected physical tenant '%s' to be reported".formatted(physicalTenantId)));
  }
}
