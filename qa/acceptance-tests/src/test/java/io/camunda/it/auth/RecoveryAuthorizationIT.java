/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.RESTORE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.client.api.search.enums.ResourceType.BACKUP;
import static io.camunda.client.api.search.enums.ResourceType.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /restore} and {@code GET /restore} are both gated strictly on {@code BACKUP:RESTORE}
 * — unlike {@code PATCH /mode}, {@code SYSTEM:UPDATE} does not reach either of them: being allowed
 * to change the cluster's mode must not imply being allowed to consume a backup or read the status
 * of an in-flight restore.
 */
@MultiDbTest
class RecoveryAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String PASSWORD = "password";

  private static final String NO_PERMISSION_USER = "noPermissionUser";
  private static final String SYSTEM_UPDATE_USER = "systemUpdateUser";
  private static final String BACKUP_RESTORE_USER = "backupRestoreUser";
  private static final String BACKUP_READ_USER = "backupReadUser";

  @UserDefinition
  private static final TestUser NO_PERMISSION_USER_DEF =
      new TestUser(NO_PERMISSION_USER, PASSWORD, List.of());

  @UserDefinition
  private static final TestUser SYSTEM_UPDATE_USER_DEF =
      new TestUser(
          SYSTEM_UPDATE_USER, PASSWORD, List.of(new Permissions(SYSTEM, UPDATE, List.of("*"))));

  @UserDefinition
  private static final TestUser BACKUP_RESTORE_USER_DEF =
      new TestUser(
          BACKUP_RESTORE_USER, PASSWORD, List.of(new Permissions(BACKUP, RESTORE, List.of("*"))));

  @UserDefinition
  private static final TestUser BACKUP_READ_USER_DEF =
      new TestUser(
          BACKUP_READ_USER, PASSWORD, List.of(new Permissions(BACKUP, READ, List.of("*"))));

  private static final String CHANGE_MODE_PATH = "v2/mode?mode=RECOVERING&dryRun=true";
  private static final String RESTORE_PATH = "v2/restore";
  private static final String RESTORE_BODY = "{\"backupIds\": [1]}";

  private static final String MODE_CHANGE_FORBIDDEN_DETAIL =
      "Unauthorized to perform any of the operations: "
          + "'RESTORE' on 'BACKUP' or 'UPDATE' on 'SYSTEM'";
  private static final String RESTORE_FORBIDDEN_DETAIL =
      "Unauthorized to perform operation 'RESTORE' on resource 'BACKUP'";

  @Test
  void shouldRejectUnauthenticatedRequest(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient client) throws Exception {
    // when the endpoint is called without credentials
    final var response = changeMode(client, null);

    // then it is rejected before any authorization decision is made
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void shouldDenyModeChangeWhenUserHasNoGrants(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient client) throws Exception {
    // when an authenticated user without any grant changes the cluster mode
    final var response = changeMode(client, NO_PERMISSION_USER);

    // then it is forbidden
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains(MODE_CHANGE_FORBIDDEN_DETAIL);
  }

  @Test
  void shouldDenyModeChangeWithUnrelatedBackupGrant(
      @Authenticated(BACKUP_READ_USER) final CamundaClient client) throws Exception {
    // when a user granted only BACKUP:READ changes the cluster mode
    final var response = changeMode(client, BACKUP_READ_USER);

    // then it is forbidden: a grant on the BACKUP resource type only reaches the mode change when
    // it is the RESTORE permission
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains(MODE_CHANGE_FORBIDDEN_DETAIL);
  }

  @Test
  void shouldAllowModeChangeWithSystemUpdateGrant(
      @Authenticated(SYSTEM_UPDATE_USER) final CamundaClient client) throws Exception {
    // when a user granted SYSTEM:UPDATE validates a transition into recovery mode
    final var response = changeMode(client, SYSTEM_UPDATE_USER);

    // then the plan is returned; dryRun leaves the cluster in its current mode
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void shouldAllowModeChangeWithBackupRestoreGrant(
      @Authenticated(BACKUP_RESTORE_USER) final CamundaClient client) throws Exception {
    // when a user granted only BACKUP:RESTORE validates a transition into recovery mode
    final var response = changeMode(client, BACKUP_RESTORE_USER);

    // then it is allowed without a second grant: the mode change is a mandatory step of the restore
    // procedure
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void shouldRejectUnauthenticatedRestoreRequest(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient client) throws Exception {
    // when the endpoint is called without credentials
    final var response = restore(client, null);

    // then it is rejected before any authorization decision is made
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void shouldRejectUnauthenticatedGetRestoreStatusRequest(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient client) throws Exception {
    // when the endpoint is called without credentials
    final var response = getRestoreStatus(client, null);

    // then it is rejected before any authorization decision is made
    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void shouldDenyRestoreWhenUserHasNoGrants(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient client) throws Exception {
    // when an authenticated user without any grant triggers a restore
    final var response = restore(client, NO_PERMISSION_USER);

    // then it is forbidden
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains(RESTORE_FORBIDDEN_DETAIL);
  }

  @Test
  void shouldDenyRestoreWithSystemUpdateGrant(
      @Authenticated(SYSTEM_UPDATE_USER) final CamundaClient client) throws Exception {
    // when a user granted only SYSTEM:UPDATE triggers a restore
    final var response = restore(client, SYSTEM_UPDATE_USER);

    // then it is forbidden: being allowed to change the cluster mode must not imply being allowed
    // to consume a backup, and the error names the permission that is actually required
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains(RESTORE_FORBIDDEN_DETAIL);
  }

  @Test
  void shouldAllowRestoreWithBackupRestoreGrant(
      @Authenticated(BACKUP_RESTORE_USER) final CamundaClient client) throws Exception {
    // when a user granted BACKUP:RESTORE triggers a restore while the cluster is processing
    final var response = restore(client, BACKUP_RESTORE_USER);

    // then authorization passes and the request is rejected on cluster state instead: the cluster
    // must be in recovery mode for the restore to be accepted
    assertThat(response.statusCode()).isEqualTo(409);
  }

  @Test
  void shouldDenyGetRestoreStatusWhenUserHasNoGrants(
      @Authenticated(NO_PERMISSION_USER) final CamundaClient client) throws Exception {
    // when an authenticated user without any grant reads the restore status
    final var response = getRestoreStatus(client, NO_PERMISSION_USER);

    // then it is forbidden
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains(RESTORE_FORBIDDEN_DETAIL);
  }

  @Test
  void shouldDenyGetRestoreStatusWithSystemUpdateGrant(
      @Authenticated(SYSTEM_UPDATE_USER) final CamundaClient client) throws Exception {
    // when a user granted only SYSTEM:UPDATE reads the restore status
    final var response = getRestoreStatus(client, SYSTEM_UPDATE_USER);

    // then it is forbidden
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains(RESTORE_FORBIDDEN_DETAIL);
  }

  @Test
  void shouldAllowGetRestoreStatusWithBackupRestoreGrant(
      @Authenticated(BACKUP_RESTORE_USER) final CamundaClient client) throws Exception {
    // when a user granted BACKUP:RESTORE reads the restore status
    final var response = getRestoreStatus(client, BACKUP_RESTORE_USER);

    // then authorization passes; no restore is in progress on this cluster
    assertThat(response.statusCode()).isEqualTo(404);
  }

  /**
   * Issues a raw HTTP call to the mode change endpoint, authenticated as {@code username} when it
   * is non-null. The endpoint has no fluent Java client method yet.
   */
  private static HttpResponse<String> changeMode(final CamundaClient client, final String username)
      throws Exception {
    final var builder = HttpRequest.newBuilder(createUri(client, CHANGE_MODE_PATH));
    if (username != null) {
      builder.header("Authorization", basicAuthentication(username));
    }
    builder.method("PATCH", BodyPublishers.noBody());
    try (final var httpClient = HttpClient.newHttpClient()) {
      return httpClient.send(builder.build(), BodyHandlers.ofString());
    }
  }

  /**
   * Issues a raw HTTP call to {@code POST /restore}, authenticated as {@code username} when it is
   * non-null. The endpoint has no fluent Java client method yet.
   */
  private static HttpResponse<String> restore(final CamundaClient client, final String username)
      throws Exception {
    final var builder = HttpRequest.newBuilder(createUri(client, RESTORE_PATH));
    if (username != null) {
      builder.header("Authorization", basicAuthentication(username));
    }
    builder.header("Content-Type", "application/json");
    builder.method("POST", BodyPublishers.ofString(RESTORE_BODY));
    try (final var httpClient = HttpClient.newHttpClient()) {
      return httpClient.send(builder.build(), BodyHandlers.ofString());
    }
  }

  /**
   * Issues a raw HTTP call to {@code GET /restore}, authenticated as {@code username} when it is
   * non-null. The endpoint has no fluent Java client method yet.
   */
  private static HttpResponse<String> getRestoreStatus(
      final CamundaClient client, final String username) throws Exception {
    final var builder = HttpRequest.newBuilder(createUri(client, RESTORE_PATH));
    if (username != null) {
      builder.header("Authorization", basicAuthentication(username));
    }
    builder.method("GET", BodyPublishers.noBody());
    try (final var httpClient = HttpClient.newHttpClient()) {
      return httpClient.send(builder.build(), BodyHandlers.ofString());
    }
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
}
