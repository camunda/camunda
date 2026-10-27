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
import java.time.Duration;
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

@ZeebeIntegration
final class PhysicalTenantHistoryBackupIT {

  private static final String REPOSITORY_NAME_DEFAULT = "history-backup-it";
  private static final String REPOSITORY_NAME_TENANT_A = "tenant-a-history-backup-it";
  private static final String TENANT_A = "tenanta";
  private static final String DEFAULT_INDEX_PREFIX = "defaulthistorybackupit";
  private static final String TENANT_A_INDEX_PREFIX = "tenantahistorybackupit";

  private static final long DEFAULT_BACKUP_ID = 101L;
  private static final long TENANT_A_BACKUP_ID = 202L;

  private static final Duration BACKUP_TIMEOUT = Duration.ofSeconds(60);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private static ElasticsearchContainer elasticsearch;
  private static PhysicalTenantsITHelper tenants;

  @AutoClose private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withUnauthenticatedAccess().withCreateSchema(true);

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

    // a repository per tenant: sharing one is rejected at boot by
    // SnapshotRepositoryIsolationValidation
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
  void shouldTakeGetListAndDeleteABackupPerPhysicalTenant() throws Exception {
    // given a backup taken on each tenant, through both path forms
    final var defaultTaken = takeBackup(DEFAULT_TENANT_ID, DEFAULT_BACKUP_ID);
    assertStatus(defaultTaken, 202);
    final var tenantATaken = takeBackup(TENANT_A, TENANT_A_BACKUP_ID);
    assertStatus(tenantATaken, 202);

    // then both tenants name their snapshots the same way — the repository is what separates them
    assertThat(scheduledSnapshots(defaultTaken))
        .isNotEmpty()
        .allSatisfy(name -> assertThat(name).startsWith("camunda_webapps_"));
    assertThat(scheduledSnapshots(tenantATaken))
        .isNotEmpty()
        .allSatisfy(name -> assertThat(name).startsWith("camunda_webapps_"));

    // and each backup completes on its own tenant
    awaitBackupState(DEFAULT_TENANT_ID, DEFAULT_BACKUP_ID, "COMPLETED");
    awaitBackupState(TENANT_A, TENANT_A_BACKUP_ID, "COMPLETED");

    // and neither tenant sees the other's backup
    assertThat(listedBackupIds(DEFAULT_TENANT_ID))
        .contains(DEFAULT_BACKUP_ID)
        .doesNotContain(TENANT_A_BACKUP_ID);
    assertThat(listedBackupIds(TENANT_A))
        .contains(TENANT_A_BACKUP_ID)
        .doesNotContain(DEFAULT_BACKUP_ID);
    assertThat(getBackup(TENANT_A, DEFAULT_BACKUP_ID).statusCode()).isEqualTo(404);
    assertThat(getBackup(DEFAULT_TENANT_ID, TENANT_A_BACKUP_ID).statusCode()).isEqualTo(404);

    // when each backup is deleted on its own tenant
    assertThat(deleteBackup(DEFAULT_TENANT_ID, DEFAULT_BACKUP_ID).statusCode()).isEqualTo(204);
    assertThat(deleteBackup(TENANT_A, TENANT_A_BACKUP_ID).statusCode()).isEqualTo(204);

    // then it is gone
    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(getBackup(DEFAULT_TENANT_ID, DEFAULT_BACKUP_ID).statusCode())
                  .isEqualTo(404);
              assertThat(getBackup(TENANT_A, TENANT_A_BACKUP_ID).statusCode()).isEqualTo(404);
            });
  }

  @Test
  void shouldRejectADuplicateBackupIdWithConflict() throws Exception {
    // given a backup already exists
    final long backupId = 303L;
    assertStatus(takeBackup(DEFAULT_TENANT_ID, backupId), 202);
    awaitBackupState(DEFAULT_TENANT_ID, backupId, "COMPLETED");

    try {
      // when the same id is requested again
      final var response = takeBackup(DEFAULT_TENANT_ID, backupId);

      // then it is a conflict, not a bad request
      assertThat(response.statusCode()).isEqualTo(409);
    } finally {
      deleteBackup(DEFAULT_TENANT_ID, backupId);
    }
  }

  /** Includes the problem detail in the failure message: a bare status tells you nothing. */
  private static void assertStatus(final HttpResponse<String> response, final int expected) {
    assertThat(response.statusCode()).as("response body: %s", response.body()).isEqualTo(expected);
  }

  private static void awaitBackupState(
      final String tenantId, final long backupId, final String expectedState) {
    Awaitility.await(
            "backup %d of tenant %s reaches %s".formatted(backupId, tenantId, expectedState))
        .atMost(BACKUP_TIMEOUT)
        .pollInterval(ofSeconds(1))
        .untilAsserted(
            () -> {
              final var response = getBackup(tenantId, backupId);
              assertStatus(response, 200);
              assertThat(JSON.readTree(response.body()).get("state").asText())
                  .isEqualTo(expectedState);
            });
  }

  private static List<Long> listedBackupIds(final String tenantId) throws Exception {
    final var response = send("GET", uri(tenantId, ""), null);
    assertThat(response.statusCode()).isEqualTo(200);
    return StreamSupport.stream(JSON.readTree(response.body()).spliterator(), false)
        .map(node -> node.get("backupId").asLong())
        .toList();
  }

  private static List<String> scheduledSnapshots(final HttpResponse<String> response)
      throws Exception {
    return StreamSupport.stream(
            JSON.readTree(response.body()).get("scheduledSnapshots").spliterator(), false)
        .map(JsonNode::asText)
        .toList();
  }

  private static HttpResponse<String> takeBackup(final String tenantId, final long backupId)
      throws Exception {
    return send("POST", uri(tenantId, ""), "{\"backupId\": " + backupId + "}");
  }

  private static HttpResponse<String> getBackup(final String tenantId, final long backupId)
      throws Exception {
    return send("GET", uri(tenantId, "/" + backupId), null);
  }

  private static HttpResponse<String> deleteBackup(final String tenantId, final long backupId)
      throws Exception {
    return send("DELETE", uri(tenantId, "/" + backupId), null);
  }

  /**
   * The {@code default} tenant is addressed on the unprefixed path, every other tenant on its
   * {@code /physical-tenants/{id}} form — both must reach the same controller.
   */
  private static URI uri(final String tenantId, final String suffix) {
    final var base = BROKER.restAddress().toString().replaceAll("/$", "");
    final var prefix = DEFAULT_TENANT_ID.equals(tenantId) ? "" : "/physical-tenants/" + tenantId;
    return URI.create(base + prefix + "/v2/backups/history" + suffix);
  }

  private static HttpResponse<String> send(final String method, final URI uri, final String body)
      throws Exception {
    final var builder = HttpRequest.newBuilder(uri);
    if (body != null) {
      builder.header("Content-Type", "application/json");
      builder.method(method, BodyPublishers.ofString(body));
    } else {
      builder.method(method, BodyPublishers.noBody());
    }
    return HTTP.send(builder.build(), BodyHandlers.ofString());
  }

  private static void createSnapshotRepository(
      final String elasticsearchUrl, final String repositoryName) throws Exception {
    try (final var documentClient =
        DocumentClient.create(elasticsearchUrl, DatabaseType.ELASTICSEARCH, EXECUTOR)) {
      documentClient.createRepository(repositoryName);
    }
  }
}
