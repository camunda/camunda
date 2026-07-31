/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.DELETE;
import static io.camunda.client.api.search.enums.PermissionType.PAUSE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.ResourceType.BACKUP;
import static io.camunda.client.api.search.enums.ResourceType.EXPORTER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end authorization coverage for the per-physical-tenant runtime backup ({@code
 * /v2/backups/runtime}) and exporting ({@code /v2/exporting}) REST endpoints against a real
 * authorization-enabled broker.
 *
 * <p>{@code RuntimeBackupServicesTest} and {@code ExportingServicesTest} mock the authorization
 * stack, so they only prove the services <em>ask</em> for the right resource/permission pair. This
 * IT proves that a real, persisted grant on the {@code BACKUP} and {@code EXPORTER} resource types
 * actually resolves to a permit (and its absence to a 403) through the full stack — both resource
 * types are new, so nothing else exercises that path.
 *
 * <p>A filesystem backup store is configured so the authorized calls genuinely succeed rather than
 * merely getting past the authorization gate.
 *
 * <p>Unlike {@code SecretAuthorizationIT}, no test needs to be disabled under a physical tenant:
 * these grants resolve correctly in that mode too, so the suite runs in full under {@code
 * -Dtest.integration.camunda.physical-tenant}.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class BackupExportingAuthorizationIT {

  /**
   * Created eagerly rather than via {@code @TempDir}: {@link #BROKER} is configured in a static
   * field initializer, which runs before JUnit resolves any injected temporary directory.
   */
  private static final Path BACKUP_DIR = createTempDirectory();

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withDataConfig(
              data -> {
                final var backup = data.getPrimaryStorage().getBackup();
                backup.setStore(BackupStoreType.FILESYSTEM);
                backup.getFilesystem().setBasePath(BACKUP_DIR.toString());
              });

  private static final String PASSWORD = "password";

  private static final String NO_PERMISSION_USER = "noPermissionUser";
  private static final String BACKUP_READ_USER = "backupReadUser";
  private static final String BACKUP_CREATE_USER = "backupCreateUser";
  private static final String BACKUP_DELETE_USER = "backupDeleteUser";
  private static final String EXPORTER_PAUSE_USER = "exporterPauseUser";

  @UserDefinition
  private static final TestUser NO_PERMISSION_USER_DEF =
      new TestUser(NO_PERMISSION_USER, PASSWORD, List.of());

  @UserDefinition
  private static final TestUser BACKUP_READ_USER_DEF =
      new TestUser(
          BACKUP_READ_USER, PASSWORD, List.of(new Permissions(BACKUP, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser BACKUP_CREATE_USER_DEF =
      new TestUser(
          BACKUP_CREATE_USER, PASSWORD, List.of(new Permissions(BACKUP, CREATE, List.of("*"))));

  @UserDefinition
  private static final TestUser BACKUP_DELETE_USER_DEF =
      new TestUser(
          BACKUP_DELETE_USER, PASSWORD, List.of(new Permissions(BACKUP, DELETE, List.of("*"))));

  @UserDefinition
  private static final TestUser EXPORTER_PAUSE_USER_DEF =
      new TestUser(
          EXPORTER_PAUSE_USER, PASSWORD, List.of(new Permissions(EXPORTER, PAUSE, List.of("*"))));

  /** Backup ids must be unique per call so a retry never collides with an earlier attempt. */
  private static final AtomicLong BACKUP_IDS = new AtomicLong(System.currentTimeMillis());

  private static final Endpoint TAKE_BACKUP = new Endpoint("POST", "v2/backups/runtime");
  private static final Endpoint LIST_BACKUPS = new Endpoint("GET", "v2/backups/runtime");
  private static final Endpoint GET_BACKUP = new Endpoint("GET", "v2/backups/runtime/1");
  private static final Endpoint GET_STATE = new Endpoint("GET", "v2/backups/runtime/state");
  private static final Endpoint SYNC_STATE = new Endpoint("POST", "v2/backups/runtime/state/sync");
  private static final Endpoint DELETE_BACKUP = new Endpoint("DELETE", "v2/backups/runtime/1");
  private static final Endpoint DELETE_STATE = new Endpoint("DELETE", "v2/backups/runtime/state");
  private static final Endpoint PAUSE_EXPORTING = new Endpoint("POST", "v2/exporting/pause");
  private static final Endpoint RESUME_EXPORTING = new Endpoint("POST", "v2/exporting/resume");
  private static final Endpoint GET_EXPORTING_STATUS = new Endpoint("GET", "v2/exporting");

  private static final List<Endpoint> BACKUP_ENDPOINTS =
      List.of(
          TAKE_BACKUP,
          LIST_BACKUPS,
          GET_BACKUP,
          GET_STATE,
          SYNC_STATE,
          DELETE_BACKUP,
          DELETE_STATE);

  private static final List<Endpoint> EXPORTING_ENDPOINTS =
      List.of(PAUSE_EXPORTING, RESUME_EXPORTING, GET_EXPORTING_STATUS);

  @AfterAll
  static void deleteBackupDirectory() throws IOException {
    if (!Files.exists(BACKUP_DIR)) {
      return;
    }
    try (final var paths = Files.walk(BACKUP_DIR)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (final IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }
  }

  static Stream<Arguments> allEndpoints() {
    return Stream.concat(BACKUP_ENDPOINTS.stream(), EXPORTING_ENDPOINTS.stream())
        .map(Arguments::of);
  }

  static Stream<Arguments> endpointsDeniedToBackupReader() {
    // BACKUP:READ must not imply write access
    return deniedToBackupGrant(TAKE_BACKUP, SYNC_STATE, DELETE_BACKUP, DELETE_STATE);
  }

  static Stream<Arguments> endpointsDeniedToBackupCreator() {
    // BACKUP:CREATE must not imply read or delete access
    return deniedToBackupGrant(LIST_BACKUPS, GET_BACKUP, GET_STATE, DELETE_BACKUP, DELETE_STATE);
  }

  static Stream<Arguments> endpointsDeniedToBackupDeleter() {
    // BACKUP:DELETE must not imply read or create access
    return deniedToBackupGrant(TAKE_BACKUP, LIST_BACKUPS, GET_BACKUP, GET_STATE, SYNC_STATE);
  }

  static Stream<Arguments> backupEndpoints() {
    // EXPORTER:PAUSE must not grant anything on the BACKUP resource type
    return BACKUP_ENDPOINTS.stream().map(Arguments::of);
  }

  /**
   * Builds the denial matrix for a caller holding a single {@code BACKUP} permission: the backup
   * endpoints that permission must not reach, plus <em>both</em> exporting endpoints — no {@code
   * BACKUP} grant may ever authorize exporting, in either direction.
   */
  private static Stream<Arguments> deniedToBackupGrant(final Endpoint... backupEndpoints) {
    return Stream.concat(Stream.of(backupEndpoints), EXPORTING_ENDPOINTS.stream())
        .map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("allEndpoints")
  void shouldRejectUnauthenticatedRequest(
      final Endpoint endpoint, @Authenticated(NO_PERMISSION_USER) final CamundaClient client)
      throws Exception {
    // when the endpoint is called without credentials — the injected client is used only to
    // discover the broker's REST base address; the request below carries no Authorization header
    final var response = send(client, endpoint, null);

    // then it is rejected before any authorization decision is made
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @ParameterizedTest
  @MethodSource("allEndpoints")
  void shouldDenyEveryEndpointWhenUserHasNoGrants(
      final Endpoint endpoint, @Authenticated(NO_PERMISSION_USER) final CamundaClient client)
      throws Exception {
    // when an authenticated user without any BACKUP or EXPORTER grant calls the endpoint
    final var response = send(client, endpoint, NO_PERMISSION_USER);

    // then it is forbidden
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @ParameterizedTest
  @MethodSource("endpointsDeniedToBackupReader")
  void shouldDenyWriteAndExportingEndpointsWithReadOnlyGrant(
      final Endpoint endpoint, @Authenticated(BACKUP_READ_USER) final CamundaClient client)
      throws Exception {
    // when a user granted only BACKUP:READ calls a backup write or an exporting endpoint
    final var response = send(client, endpoint, BACKUP_READ_USER);

    // then it is forbidden: READ implies neither CREATE nor DELETE, and grants nothing on EXPORTER
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @ParameterizedTest
  @MethodSource("endpointsDeniedToBackupCreator")
  void shouldDenyReadDeleteAndExportingEndpointsWithCreateOnlyGrant(
      final Endpoint endpoint, @Authenticated(BACKUP_CREATE_USER) final CamundaClient client)
      throws Exception {
    // when a user granted only BACKUP:CREATE calls a read, delete or exporting endpoint
    final var response = send(client, endpoint, BACKUP_CREATE_USER);

    // then it is forbidden
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @ParameterizedTest
  @MethodSource("endpointsDeniedToBackupDeleter")
  void shouldDenyReadCreateAndExportingEndpointsWithDeleteOnlyGrant(
      final Endpoint endpoint, @Authenticated(BACKUP_DELETE_USER) final CamundaClient client)
      throws Exception {
    // when a user granted only BACKUP:DELETE calls a read, create or exporting endpoint
    final var response = send(client, endpoint, BACKUP_DELETE_USER);

    // then it is forbidden
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @ParameterizedTest
  @MethodSource("backupEndpoints")
  void shouldDenyBackupEndpointsWithExporterGrantOnly(
      final Endpoint endpoint, @Authenticated(EXPORTER_PAUSE_USER) final CamundaClient client)
      throws Exception {
    // when a user granted only EXPORTER:PAUSE calls a backup endpoint
    final var response = send(client, endpoint, EXPORTER_PAUSE_USER);

    // then it is forbidden: a grant on one resource type must not leak into the other
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void shouldAllowReadEndpointsWithReadGrant(
      @Authenticated(BACKUP_READ_USER) final CamundaClient client) throws Exception {
    // when a user granted BACKUP:READ lists backups and queries the runtime state
    // then both succeed
    assertThat(send(client, LIST_BACKUPS, BACKUP_READ_USER).statusCode()).isEqualTo(200);
    assertThat(send(client, GET_STATE, BACKUP_READ_USER).statusCode()).isEqualTo(200);
  }

  @Test
  void shouldAllowTakingBackupWithCreateGrant(
      @Authenticated(BACKUP_CREATE_USER) final CamundaClient client) throws Exception {
    // when a user granted BACKUP:CREATE takes a backup
    final var response =
        send(client, TAKE_BACKUP, BACKUP_CREATE_USER, takeBackupBody(BACKUP_IDS.incrementAndGet()));

    // then it is accepted
    assertThat(response.statusCode()).isEqualTo(202);
  }

  @Test
  void shouldAllowSyncingRuntimeStateWithCreateGrant(
      @Authenticated(BACKUP_CREATE_USER) final CamundaClient client) throws Exception {
    // when a user granted BACKUP:CREATE force-writes the runtime state
    // (sync creates backup metadata, so it is gated on CREATE rather than READ)
    final var response = send(client, SYNC_STATE, BACKUP_CREATE_USER);

    // then it succeeds
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void shouldAllowDeletingRuntimeStateWithDeleteGrant(
      @Authenticated(BACKUP_DELETE_USER) final CamundaClient client) throws Exception {
    // when a user granted BACKUP:DELETE deletes the runtime state
    final var response = send(client, DELETE_STATE, BACKUP_DELETE_USER);

    // then it succeeds
    assertThat(response.statusCode()).isEqualTo(204);
  }

  @Test
  void shouldAllowPausingAndResumingExportingWithPauseGrant(
      @Authenticated(EXPORTER_PAUSE_USER) final CamundaClient client) throws Exception {
    try {
      // when a user granted EXPORTER:PAUSE pauses exporting
      final var pauseResponse = send(client, PAUSE_EXPORTING, EXPORTER_PAUSE_USER);

      // then it succeeds
      assertThat(pauseResponse.statusCode()).isEqualTo(204);

      // and the same grant authorizes resuming: EXPORTER only defines PAUSE, so a caller able to
      // stop exporting must be able to start it again
      final var resumeResponse = send(client, RESUME_EXPORTING, EXPORTER_PAUSE_USER);
      assertThat(resumeResponse.statusCode()).isEqualTo(204);
    } finally {
      // exporting is broker-wide: leaving it paused would starve the secondary storage that every
      // other test in this class reads its authorizations from
      send(client, RESUME_EXPORTING, EXPORTER_PAUSE_USER);
    }
  }

  @Test
  void shouldAllowReadingExportingStatusWithPauseGrant(
      @Authenticated(EXPORTER_PAUSE_USER) final CamundaClient client) throws Exception {
    // when a user granted EXPORTER:PAUSE reads the exporting status
    final var response = send(client, GET_EXPORTING_STATUS, EXPORTER_PAUSE_USER);

    // then it succeeds: EXPORTER only defines PAUSE, so the same grant gates the read
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("EXPORTING");
  }

  /**
   * Issues a raw HTTP call to the given endpoint, authenticated as {@code username} when it is
   * non-null. These endpoints have no fluent Java client methods yet.
   */
  private static HttpResponse<String> send(
      final CamundaClient client, final Endpoint endpoint, final String username) throws Exception {
    return send(client, endpoint, username, null);
  }

  private static HttpResponse<String> send(
      final CamundaClient client, final Endpoint endpoint, final String username, final String body)
      throws Exception {
    final var builder = HttpRequest.newBuilder(createUri(client, endpoint.path()));
    if (username != null) {
      builder.header("Authorization", basicAuthentication(username));
    }

    // A valid body is sent to POST /v2/backups/runtime so denial tests fail the authorization
    // check rather than request validation; the other endpoints declare no request body.
    final var effectiveBody =
        body == null && TAKE_BACKUP.equals(endpoint)
            ? takeBackupBody(BACKUP_IDS.incrementAndGet())
            : body;
    if (effectiveBody != null) {
      builder.header("Content-Type", "application/json");
      builder.method(endpoint.method(), BodyPublishers.ofString(effectiveBody));
    } else {
      builder.method(endpoint.method(), BodyPublishers.noBody());
    }

    return HttpClient.newHttpClient().send(builder.build(), BodyHandlers.ofString());
  }

  private static String takeBackupBody(final long backupId) {
    return "{\"backupId\": " + backupId + "}";
  }

  private static String basicAuthentication(final String username) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
  }

  private static URI createUri(final CamundaClient client, final String path) {
    final var base = client.getConfiguration().getRestAddress().toString();
    final var separator = base.endsWith("/") ? "" : "/";
    return URI.create(base + separator + path);
  }

  private static Path createTempDirectory() {
    try {
      return Files.createTempDirectory("backup-exporting-authorization-it");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A REST endpoint under test, identified by HTTP method and path relative to the REST base. */
  private record Endpoint(String method, String path) {

    @Override
    public String toString() {
      return method + " /" + path;
    }
  }
}
