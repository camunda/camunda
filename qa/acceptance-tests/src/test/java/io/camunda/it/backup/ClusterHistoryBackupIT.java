/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.it.document.DocumentClient;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * The cluster-wide history backup endpoints against a real Elasticsearch and two physical tenants.
 *
 * <p>Exercises the two rules the contract turns on, neither of which a mocked-port test can prove
 * over real storage: a backup only one physical tenant holds is read and deleted cluster-wide as a
 * success, and a backup id one tenant already holds fails the whole request without scheduling
 * anything anywhere.
 *
 * <p>Each tenant gets its own snapshot repository, unlike {@link PhysicalTenantHistoryBackupIT},
 * which deliberately shares one to prove tenants stay separated by snapshot name.
 */
@ZeebeIntegration
final class ClusterHistoryBackupIT {

  private static final String TENANT_A = "tenanta";
  private static final String REPOSITORY_NAME_DEFAULT = "cluster-history-backup-it";
  private static final String REPOSITORY_NAME_TENANT_A = "tenant-a-cluster-history-backup-it";
  private static final String DEFAULT_INDEX_PREFIX = "defaultclusterhistorybackupit";
  private static final String TENANT_A_INDEX_PREFIX = "tenantaclusterhistorybackupit";

  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";

  private static final Duration BACKUP_TIMEOUT = Duration.ofSeconds(60);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private static ElasticsearchContainer elasticsearch;
  private static PhysicalTenantsITHelper tenants;

  @AutoClose private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          // The per-tenant /v2 endpoints stay open, so the test can stage a single-tenant backup
          // without a user; /cluster/v2 keeps its own credentials regardless.
          .withUnauthenticatedAccess()
          .withCreateSchema(true)
          .withProperty("camunda.security.cluster-admin.basic.users[0].name", CLUSTER_ADMIN_USER)
          .withProperty(
              "camunda.security.cluster-admin.basic.users[0].password", CLUSTER_ADMIN_PASSWORD);

  @BeforeAll
  static void startBrokerAgainstSharedElasticsearch() throws Exception {
    elasticsearch =
        TestSearchContainers.createDefaultElasticsearchContainer()
            .withStartupTimeout(Duration.ofMinutes(5))
            // filesystem snapshot repositories are only allowed under a registered path
            .withEnv("path.repo", "~/");
    elasticsearch.start();

    final String url = "http://" + elasticsearch.getHttpHostAddress();
    createSnapshotRepository(url, REPOSITORY_NAME_DEFAULT);
    createSnapshotRepository(url, REPOSITORY_NAME_TENANT_A);

    tenants =
        PhysicalTenantsITHelper.builder()
            .withTenant(DEFAULT_TENANT_ID, Storage.elasticsearch(url, DEFAULT_INDEX_PREFIX))
            .withTenant(TENANT_A, Storage.elasticsearch(url, TENANT_A_INDEX_PREFIX))
            .build();
    tenants.configure(BROKER);

    BROKER.withUnifiedConfig(
        camunda ->
            camunda
                .getData()
                .getSecondaryStorage()
                .getElasticsearch()
                .getBackup()
                .setRepositoryName(REPOSITORY_NAME_DEFAULT));
    BROKER.withPtConfig(
        TENANT_A,
        camunda ->
            camunda
                .getData()
                .getSecondaryStorage()
                .getElasticsearch()
                .getBackup()
                .setRepositoryName(REPOSITORY_NAME_TENANT_A));

    BROKER.start();
  }

  @AfterAll
  static void stopElasticsearch() {
    if (elasticsearch != null) {
      elasticsearch.stop();
    }
  }

  @Test
  void shouldTakeGetAndDeleteABackupOnEveryPhysicalTenant() throws Exception {
    // given
    final long backupId = 501L;

    try {
      // when
      final var taken = clusterTakeBackup(backupId, null);

      // then every targeted tenant is reported, in tenant-id order
      assertStatus(taken, 202);
      final var body = JSON.readTree(taken.body());
      assertThat(body.get("backupId").asLong()).isEqualTo(backupId);
      assertThat(physicalTenantIds(body.get("physicalTenants")))
          .containsExactly(DEFAULT_TENANT_ID, TENANT_A);
      assertThat(body.get("physicalTenants"))
          .allSatisfy(tenant -> assertThat(tenant.get("scheduledSnapshots")).isNotEmpty());

      // and the backup completes on both of them
      awaitClusterBackupState(backupId, "COMPLETED", "COMPLETED");

      // and it is listed under one group covering both tenants
      final var listed = clusterListBackups(null);
      assertStatus(listed, 200);
      final var group = groupFor(JSON.readTree(listed.body()), backupId);
      assertThat(physicalTenantIds(group.get("physicalTenants")))
          .containsExactly(DEFAULT_TENANT_ID, TENANT_A);
    } finally {
      clusterDeleteBackup(backupId, null);
    }

    // and deleting it cluster-wide leaves nothing behind
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(clusterGetBackup(backupId, null).statusCode()).isEqualTo(404));
  }

  /**
   * A backup that exists on one physical tenant and not another is normal — the cluster-admin can
   * narrow a take to one tenant, and so can that tenant's own operator. Reading it cluster-wide is
   * therefore a 200 naming both tenants, and deleting it a 204, not a partial-failure status.
   */
  @Test
  void shouldReadAndDeleteASingleTenantBackupClusterWideAsSuccess() throws Exception {
    // given a backup taken on one physical tenant only, through that tenant's own endpoint
    final long backupId = 502L;
    assertStatus(takeBackupOnTenant(TENANT_A, backupId), 202);

    final HttpResponse<String> deleted;
    try {
      awaitClusterBackupState(backupId, "NOT_FOUND", "COMPLETED");

      // when reading it cluster-wide
      final var read = clusterGetBackup(backupId, null);

      // then the tenant holding nothing is reported as observed, not as a failure
      assertStatus(read, 200);
      final var physicalTenants = JSON.readTree(read.body()).get("physicalTenants");
      assertThat(stateOf(physicalTenants, DEFAULT_TENANT_ID)).isEqualTo("NOT_FOUND");
      assertThat(stateOf(physicalTenants, TENANT_A)).isEqualTo("COMPLETED");

      // and only the tenant holding it appears in the listing group
      final var listed = clusterListBackups(null);
      assertStatus(listed, 200);
      assertThat(
              physicalTenantIds(
                  groupFor(JSON.readTree(listed.body()), backupId).get("physicalTenants")))
          .containsExactly(TENANT_A);
    } finally {
      // when deleting it cluster-wide — also the teardown, so a failure above cannot leave the
      // snapshot in the repository the other tests list
      deleted = clusterDeleteBackup(backupId, null);
    }

    // then the tenant that never held it counts as already deleted
    assertStatus(deleted, 204);
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(clusterGetBackup(backupId, null).statusCode()).isEqualTo(404));
  }

  @Test
  void shouldScheduleNothingWhenOnePhysicalTenantAlreadyHoldsTheBackupId() throws Exception {
    // given a backup id one physical tenant already holds
    final long backupId = 503L;
    assertStatus(takeBackupOnTenant(TENANT_A, backupId), 202);
    awaitClusterBackupState(backupId, "NOT_FOUND", "COMPLETED");

    try {
      // when the same id is requested cluster-wide
      final var taken = clusterTakeBackup(backupId, null);

      // then the whole request is rejected
      assertThat(taken.statusCode()).isEqualTo(409);

      // and the other tenant still holds nothing, so nothing was scheduled anywhere
      assertThat(getBackupOnTenant(DEFAULT_TENANT_ID, backupId).statusCode()).isEqualTo(404);
    } finally {
      clusterDeleteBackup(backupId, null);
    }
  }

  @Test
  void shouldNarrowEveryOperationToOnePhysicalTenant() throws Exception {
    // given
    final long backupId = 504L;

    try {
      // when the take is narrowed to one physical tenant
      final var taken = clusterTakeBackup(backupId, TENANT_A);

      // then only that tenant is scheduled
      assertStatus(taken, 202);
      assertThat(physicalTenantIds(JSON.readTree(taken.body()).get("physicalTenants")))
          .containsExactly(TENANT_A);
      assertThat(getBackupOnTenant(DEFAULT_TENANT_ID, backupId).statusCode()).isEqualTo(404);

      // and reading narrowed to the other tenant is a plain 404, as on its own endpoint
      assertThat(clusterGetBackup(backupId, DEFAULT_TENANT_ID).statusCode()).isEqualTo(404);
    } finally {
      clusterDeleteBackup(backupId, TENANT_A);
    }
  }

  @Test
  void shouldRejectAnUnknownPhysicalTenantId() throws Exception {
    // when - then
    assertThat(clusterGetBackup(505L, "nosuchtenant").statusCode()).isEqualTo(404);
  }

  @Test
  void shouldRejectClusterWideBackupRequestsWithoutClusterAdminCredentials() throws Exception {
    // when - then the per-tenant endpoints are unprotected here, the cluster-admin ones never are
    assertThat(send("GET", clusterUri("", null), null, false).statusCode()).isEqualTo(401);
  }

  private static void awaitClusterBackupState(
      final long backupId, final String defaultTenantState, final String tenantAState) {
    Awaitility.await(
            "backup %d reaches [%s, %s]".formatted(backupId, defaultTenantState, tenantAState))
        .atMost(BACKUP_TIMEOUT)
        .pollInterval(ofSeconds(1))
        .untilAsserted(
            () -> {
              final var response = clusterGetBackup(backupId, null);
              assertStatus(response, 200);
              final var physicalTenants = JSON.readTree(response.body()).get("physicalTenants");
              assertThat(stateOf(physicalTenants, DEFAULT_TENANT_ID)).isEqualTo(defaultTenantState);
              assertThat(stateOf(physicalTenants, TENANT_A)).isEqualTo(tenantAState);
            });
  }

  private static String stateOf(final JsonNode physicalTenants, final String physicalTenantId) {
    return StreamSupport.stream(physicalTenants.spliterator(), false)
        .filter(tenant -> physicalTenantId.equals(tenant.get("physicalTenantId").asText()))
        .map(tenant -> tenant.get("state").asText())
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Expected physical tenant '%s' in %s"
                        .formatted(physicalTenantId, physicalTenants)));
  }

  private static List<String> physicalTenantIds(final JsonNode physicalTenants) {
    return StreamSupport.stream(physicalTenants.spliterator(), false)
        .map(tenant -> tenant.get("physicalTenantId").asText())
        .toList();
  }

  private static JsonNode groupFor(final JsonNode listing, final long backupId) {
    return StreamSupport.stream(listing.spliterator(), false)
        .filter(group -> group.get("backupId").asLong() == backupId)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("Expected backup %d in %s".formatted(backupId, listing)));
  }

  /** Includes the problem detail in the failure message: a bare status tells you nothing. */
  private static void assertStatus(final HttpResponse<String> response, final int expected) {
    assertThat(response.statusCode()).as("response body: %s", response.body()).isEqualTo(expected);
  }

  private static HttpResponse<String> clusterTakeBackup(
      final long backupId, final String physicalTenantId) throws Exception {
    return send("POST", clusterUri("", physicalTenantId), "{\"backupId\": " + backupId + "}", true);
  }

  private static HttpResponse<String> clusterListBackups(final String physicalTenantId)
      throws Exception {
    return send("GET", clusterUri("", physicalTenantId), null, true);
  }

  private static HttpResponse<String> clusterGetBackup(
      final long backupId, final String physicalTenantId) throws Exception {
    return send("GET", clusterUri("/" + backupId, physicalTenantId), null, true);
  }

  private static HttpResponse<String> clusterDeleteBackup(
      final long backupId, final String physicalTenantId) throws Exception {
    return send("DELETE", clusterUri("/" + backupId, physicalTenantId), null, true);
  }

  private static HttpResponse<String> takeBackupOnTenant(
      final String physicalTenantId, final long backupId) throws Exception {
    return send("POST", tenantUri(physicalTenantId, ""), "{\"backupId\": " + backupId + "}", false);
  }

  private static HttpResponse<String> getBackupOnTenant(
      final String physicalTenantId, final long backupId) throws Exception {
    return send("GET", tenantUri(physicalTenantId, "/" + backupId), null, false);
  }

  private static URI clusterUri(final String suffix, final String physicalTenantId) {
    final var query = physicalTenantId == null ? "" : "?physicalTenantId=" + physicalTenantId;
    return URI.create(base() + "/cluster/v2/backups/history" + suffix + query);
  }

  /**
   * The {@code default} tenant is addressed on the unprefixed path, every other tenant on its
   * {@code /physical-tenants/{id}} form.
   */
  private static URI tenantUri(final String physicalTenantId, final String suffix) {
    final var prefix =
        DEFAULT_TENANT_ID.equals(physicalTenantId) ? "" : "/physical-tenants/" + physicalTenantId;
    return URI.create(base() + prefix + "/v2/backups/history" + suffix);
  }

  private static String base() {
    return BROKER.restAddress().toString().replaceAll("/$", "");
  }

  private static HttpResponse<String> send(
      final String method, final URI uri, final String body, final boolean asClusterAdmin)
      throws Exception {
    final var builder = HttpRequest.newBuilder(uri);
    if (asClusterAdmin) {
      builder.header("Authorization", basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD));
    }
    if (body != null) {
      builder.header("Content-Type", "application/json");
      builder.method(method, BodyPublishers.ofString(body));
    } else {
      builder.method(method, BodyPublishers.noBody());
    }
    return HTTP.send(builder.build(), BodyHandlers.ofString());
  }

  private static String basicAuth(final String user, final String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private static void createSnapshotRepository(
      final String elasticsearchUrl, final String repositoryName) throws Exception {
    try (final var documentClient =
        DocumentClient.create(elasticsearchUrl, DatabaseType.ELASTICSEARCH, EXECUTOR)) {
      documentClient.createRepository(repositoryName);
    }
  }
}
