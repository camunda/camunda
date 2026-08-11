/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.backup.HistoryBackupApi;
import io.camunda.service.backup.HistoryBackupState;
import io.camunda.service.backup.HistoryBackupStateCode;
import io.camunda.service.backup.HistoryBackupTaken;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HistoryBackupServicesTest {

  private static final String PHYSICAL_TENANT_ID = "testtenant";

  private final HistoryBackupApi api = mock(HistoryBackupApi.class);
  private final AuthorizationChecker authorizationChecker = mock(AuthorizationChecker.class);
  private final AuthorizationsConfiguration authorizationsConfig =
      new AuthorizationsConfiguration();
  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);

  private ExecutorService executor;
  private HistoryBackupServices services;

  @BeforeEach
  public void before() {
    authorizationsConfig.setEnabled(false);
    executor = Executors.newSingleThreadExecutor();
    // A bare executor, not `new ApiServicesExecutorProvider(executor)`: the provider wraps it in
    // the
    // physical-tenant propagating decorator, whose spring-web dependency is not on this module's
    // test classpath. Propagation is not what this test is about.
    final var executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(executor);
    services =
        new HistoryBackupServices(
            PHYSICAL_TENANT_ID, api, authorizationChecker, authorizationsConfig, executorProvider);
  }

  @AfterEach
  public void after() {
    executor.shutdownNow();
  }

  @Test
  public void shouldTakeBackupForItsOwnPhysicalTenant() {
    // given
    when(api.takeBackup(PHYSICAL_TENANT_ID, 42L))
        .thenReturn(new HistoryBackupTaken(42L, List.of("snapshot-1")));

    // when
    final var future = services.takeBackup(42L, authentication);

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(1))
        .isEqualTo(new HistoryBackupTaken(42L, List.of("snapshot-1")));
    verify(api).takeBackup(PHYSICAL_TENANT_ID, 42L);
  }

  @Test
  public void shouldRejectNonPositiveBackupIdOnTake() {
    // when
    final var future = services.takeBackup(0L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.INVALID_ARGUMENT);
    verify(api, never()).takeBackup(anyString(), anyLong());
  }

  @Test
  public void shouldRejectMissingBackupIdOnTake() {
    // when history backups have no generated-id mode, so an absent id is a bad request
    final var future = services.takeBackup(null, authentication);

    // then
    assertServiceExceptionStatus(future, Status.INVALID_ARGUMENT);
    verify(api, never()).takeBackup(anyString(), anyLong());
  }

  @Test
  public void shouldRejectNonPositiveBackupIdOnGet() {
    // when
    final var future = services.getBackupState(-1L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.INVALID_ARGUMENT);
    verify(api, never()).getBackupState(anyString(), anyLong());
  }

  @Test
  public void shouldRejectNonPositiveBackupIdOnDelete() {
    // when
    final var future = services.deleteBackup(0L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.INVALID_ARGUMENT);
    verify(api, never()).deleteBackup(anyString(), anyLong());
  }

  @Test
  public void shouldGetBackupStateForItsOwnPhysicalTenant() {
    // given
    final var state = completedState(42L);
    when(api.getBackupState(PHYSICAL_TENANT_ID, 42L)).thenReturn(state);

    // when
    final var future = services.getBackupState(42L, authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(state);
  }

  @Test
  public void shouldListBackupsWithTheGivenPrefixAndVerbosity() {
    // given
    when(api.getBackups(PHYSICAL_TENANT_ID, false, "17*")).thenReturn(List.of(completedState(17L)));

    // when
    final var future = services.listBackups("17*", false, authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(api).getBackups(PHYSICAL_TENANT_ID, false, "17*");
  }

  @Test
  public void shouldListAllBackupsWhenPrefixIsOmitted() {
    // given
    when(api.getBackups(PHYSICAL_TENANT_ID, true, "*")).thenReturn(List.of());

    // when
    final var future = services.listBackups(null, true, authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(api).getBackups(PHYSICAL_TENANT_ID, true, "*");
  }

  @Test
  public void shouldRejectPrefixNotEndingInWildcard() {
    // when
    final var future = services.listBackups("17", true, authentication);

    // then
    assertServiceExceptionStatus(future, Status.INVALID_ARGUMENT);
    verify(api, never()).getBackups(anyString(), anyBoolean(), any());
  }

  @Test
  public void shouldDeleteBackupForItsOwnPhysicalTenant() {
    // when
    final var future = services.deleteBackup(42L, authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(api).deleteBackup(PHYSICAL_TENANT_ID, 42L);
  }

  @Test
  public void shouldMapPortFailuresToTheirServiceStatus() {
    // given
    when(api.getBackupState(PHYSICAL_TENANT_ID, 42L))
        .thenThrow(new ServiceException("gone", Status.NOT_FOUND));

    // when
    final var future = services.getBackupState(42L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.NOT_FOUND);
  }

  @Test
  public void shouldRejectTakeWhenUserHasNoCreatePermission() {
    // given
    grantBackupPermissions(PermissionType.READ, PermissionType.DELETE);

    // when
    final var future = services.takeBackup(42L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.FORBIDDEN);
    verify(api, never()).takeBackup(anyString(), anyLong());
  }

  @Test
  public void shouldTakeWhenUserHasCreatePermission() {
    // given
    grantBackupPermissions(PermissionType.CREATE);
    when(api.takeBackup(PHYSICAL_TENANT_ID, 42L))
        .thenReturn(new HistoryBackupTaken(42L, List.of()));

    // when
    final var future = services.takeBackup(42L, authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  public void shouldRejectGetWhenUserHasNoReadPermission() {
    // given
    grantBackupPermissions(PermissionType.CREATE, PermissionType.DELETE);

    // when
    final var future = services.getBackupState(42L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.FORBIDDEN);
    verify(api, never()).getBackupState(anyString(), anyLong());
  }

  @Test
  public void shouldRejectListWhenUserHasNoReadPermission() {
    // given
    grantBackupPermissions(PermissionType.CREATE, PermissionType.DELETE);

    // when
    final var future = services.listBackups(null, true, authentication);

    // then
    assertServiceExceptionStatus(future, Status.FORBIDDEN);
    verify(api, never()).getBackups(anyString(), anyBoolean(), any());
  }

  @Test
  public void shouldRejectDeleteWhenUserHasNoDeletePermission() {
    // given
    grantBackupPermissions(PermissionType.READ, PermissionType.CREATE);

    // when
    final var future = services.deleteBackup(42L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.FORBIDDEN);
    verify(api, never()).deleteBackup(anyString(), anyLong());
  }

  @Test
  public void shouldDeleteWhenUserHasDeletePermission() {
    // given
    grantBackupPermissions(PermissionType.DELETE);

    // when
    final var future = services.deleteBackup(42L, authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(api).deleteBackup(PHYSICAL_TENANT_ID, 42L);
  }

  private static HistoryBackupState completedState(final long backupId) {
    return new HistoryBackupState(backupId, HistoryBackupStateCode.COMPLETED, null, List.of());
  }

  /**
   * Enables authorizations and grants the given permissions on the {@code BACKUP} resource type
   * only, so that a check against any other resource type resolves to "no permissions".
   */
  private void grantBackupPermissions(final PermissionType... permissions) {
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());
    when(authorizationChecker.collectPermissionTypes(
            any(), eq(AuthorizationResourceType.BACKUP), any()))
        .thenReturn(Set.of(permissions));
  }

  private void assertServiceExceptionStatus(
      final CompletableFuture<?> future, final Status expectedStatus) {
    final var exception = catchThrowable(future::join).getCause();
    assertThat(exception).isInstanceOf(ServiceException.class);
    assertThat(((ServiceException) exception).getStatus()).isEqualTo(expectedStatus);
  }
}
