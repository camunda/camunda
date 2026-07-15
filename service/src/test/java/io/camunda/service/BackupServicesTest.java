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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.backup.client.api.BackupAlreadyExistException;
import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.IncompleteTopologyException;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BackupServicesTest {

  private static final String PHYSICAL_TENANT_ID = "test-tenant";

  private final BackupApi backupApi = mock(BackupApi.class);
  private final AuthorizationChecker authorizationChecker = mock(AuthorizationChecker.class);
  private final AuthorizationsConfiguration authorizationsConfig =
      new AuthorizationsConfiguration();
  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);
  private BackupServices manualModeServices;
  private BackupServices generatedIdServices;

  @BeforeEach
  public void before() {
    authorizationsConfig.setEnabled(false);
    manualModeServices = services(false);
    generatedIdServices = services(true);
  }

  private BackupServices services(final boolean backupIdGenerated) {
    return new BackupServices(
        PHYSICAL_TENANT_ID,
        mock(BrokerClient.class),
        mock(SecurityContextProvider.class),
        backupApi,
        authorizationChecker,
        authorizationsConfig,
        backupIdGenerated,
        mock(ApiServicesExecutorProvider.class),
        null);
  }

  @Test
  public void takeBackupShouldDelegateWithExplicitId() {
    // given
    when(backupApi.takeBackup(PHYSICAL_TENANT_ID, 42L))
        .thenReturn(CompletableFuture.completedFuture(42L));

    // when
    final var future = manualModeServices.takeBackup(OptionalLong.of(42L), authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(backupApi).takeBackup(PHYSICAL_TENANT_ID, 42L);
  }

  @Test
  public void takeBackupShouldRejectMissingIdInManualMode() {
    // when
    final var future = manualModeServices.takeBackup(OptionalLong.empty(), authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertServiceExceptionStatus(future, Status.INVALID_ARGUMENT);
    verify(backupApi, never()).takeBackup(any(), any(Long.class));
  }

  @Test
  public void takeBackupShouldRejectNonPositiveIdInManualMode() {
    // when
    final var future = manualModeServices.takeBackup(OptionalLong.of(0L), authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertServiceExceptionStatus(future, Status.INVALID_ARGUMENT);
  }

  @Test
  public void takeBackupShouldGenerateIdWhenBackupIdGenerated() {
    // given
    when(backupApi.takeBackup(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(123L));

    // when
    final var future = generatedIdServices.takeBackup(OptionalLong.empty(), authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(backupApi).takeBackup(PHYSICAL_TENANT_ID);
  }

  @Test
  public void takeBackupShouldRejectExplicitIdWhenBackupIdGenerated() {
    // when
    final var future = generatedIdServices.takeBackup(OptionalLong.of(1L), authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertServiceExceptionStatus(future, Status.INVALID_ARGUMENT);
    verify(backupApi, never()).takeBackup(any(), any(Long.class));
  }

  @Test
  public void takeBackupShouldMapBackupAlreadyExistExceptionToAlreadyExists() {
    // given
    when(backupApi.takeBackup(PHYSICAL_TENANT_ID, 42L))
        .thenReturn(CompletableFuture.failedFuture(new BackupAlreadyExistException(42L, 43L)));

    // when
    final var future = manualModeServices.takeBackup(OptionalLong.of(42L), authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertServiceExceptionStatus(future, Status.ALREADY_EXISTS);
  }

  @Test
  public void takeBackupShouldMapIncompleteTopologyExceptionToUnavailable() {
    // given
    when(backupApi.takeBackup(PHYSICAL_TENANT_ID, 42L))
        .thenThrow(new IncompleteTopologyException("incomplete"));

    // when
    final var future = manualModeServices.takeBackup(OptionalLong.of(42L), authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertServiceExceptionStatus(future, Status.UNAVAILABLE);
  }

  @Test
  public void takeBackupShouldCompleteExceptionallyWhenUserHasNoAuthorizations() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = manualModeServices.takeBackup(OptionalLong.of(42L), authentication);

    // then
    assertServiceExceptionStatus(future, Status.FORBIDDEN);
    verify(backupApi, never()).takeBackup(any(), any(Long.class));
  }

  @Test
  public void getBackupStatusShouldDelegateWhenAuthorized() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));
    final var status = new BackupStatus(42L, State.COMPLETED, Optional.empty(), List.of());
    when(backupApi.getStatus(PHYSICAL_TENANT_ID, 42L))
        .thenReturn(CompletableFuture.completedFuture(status));

    // when
    final var future = manualModeServices.getBackupStatus(42L, authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(status);
  }

  @Test
  public void getBackupStatusShouldMapDoesNotExistToNotFound() {
    // given
    final var status = new BackupStatus(42L, State.DOES_NOT_EXIST, Optional.empty(), List.of());
    when(backupApi.getStatus(PHYSICAL_TENANT_ID, 42L))
        .thenReturn(CompletableFuture.completedFuture(status));

    // when
    final var future = manualModeServices.getBackupStatus(42L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.NOT_FOUND);
  }

  @Test
  public void getBackupStatusShouldCompleteExceptionallyWhenUserHasNoReadPermission() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = manualModeServices.getBackupStatus(42L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.FORBIDDEN);
    verify(backupApi, never()).getStatus(any(), any(Long.class));
  }

  @Test
  public void listBackupsShouldDefaultToWildcard() {
    // given
    when(backupApi.listBackups(PHYSICAL_TENANT_ID, BackupApi.WILDCARD))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    final var future = manualModeServices.listBackups(Optional.empty(), authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(backupApi).listBackups(PHYSICAL_TENANT_ID, BackupApi.WILDCARD);
  }

  @Test
  public void listBackupsShouldRejectPrefixNotEndingInWildcard() {
    // when
    final var future = manualModeServices.listBackups(Optional.of("12"), authentication);

    // then
    assertServiceExceptionStatus(future, Status.INVALID_ARGUMENT);
    verify(backupApi, never()).listBackups(any(), any());
  }

  @Test
  public void listBackupsShouldForwardValidPrefix() {
    // given
    when(backupApi.listBackups(PHYSICAL_TENANT_ID, "12*"))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    final var future = manualModeServices.listBackups(Optional.of("12*"), authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(backupApi).listBackups(PHYSICAL_TENANT_ID, "12*");
  }

  @Test
  public void deleteBackupShouldDelegate() {
    // given
    when(backupApi.deleteBackup(PHYSICAL_TENANT_ID, 42L))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = manualModeServices.deleteBackup(42L, authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(backupApi).deleteBackup(PHYSICAL_TENANT_ID, 42L);
  }

  @Test
  public void deleteBackupShouldCompleteExceptionallyWhenUserHasNoUpdatePermission() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));

    // when
    final var future = manualModeServices.deleteBackup(42L, authentication);

    // then
    assertServiceExceptionStatus(future, Status.FORBIDDEN);
    verify(backupApi, never()).deleteBackup(any(), any(Long.class));
  }

  @Test
  public void getRuntimeStateShouldCombineCheckpointStateAndRanges() {
    // given
    final var checkpointState = new CheckpointStateResponse();
    final var ranges = new BackupRangesResponse();
    when(backupApi.getCheckpointState(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(checkpointState));
    when(backupApi.getBackupRanges(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(ranges));

    // when
    final var future = manualModeServices.getRuntimeState(authentication);

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            state -> {
              assertThat(state.checkpointState()).isEqualTo(checkpointState);
              assertThat(state.ranges()).isEqualTo(ranges);
            });
  }

  @Test
  public void getRuntimeStateShouldFailFastWhenCheckpointStateFails() {
    // given
    when(backupApi.getCheckpointState(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedFuture(new IncompleteTopologyException("incomplete")));
    when(backupApi.getBackupRanges(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(new BackupRangesResponse()));

    // when
    final var future = manualModeServices.getRuntimeState(authentication);

    // then
    assertServiceExceptionStatus(future, Status.UNAVAILABLE);
  }

  @Test
  public void getRuntimeStateShouldFailFastWhenRangesFail() {
    // given
    when(backupApi.getCheckpointState(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(new CheckpointStateResponse()));
    when(backupApi.getBackupRanges(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedFuture(new IncompleteTopologyException("incomplete")));

    // when
    final var future = manualModeServices.getRuntimeState(authentication);

    // then
    assertServiceExceptionStatus(future, Status.UNAVAILABLE);
  }

  @Test
  public void syncRuntimeStateShouldCombineSyncAndCheckpointState() {
    // given
    final var checkpointState = new CheckpointStateResponse();
    final var ranges = new BackupRangesResponse();
    when(backupApi.syncMetadata(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(ranges));
    when(backupApi.getCheckpointState(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(checkpointState));

    // when
    final var future = manualModeServices.syncRuntimeState(authentication);

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            state -> {
              assertThat(state.checkpointState()).isEqualTo(checkpointState);
              assertThat(state.ranges()).isEqualTo(ranges);
            });
  }

  @Test
  public void syncRuntimeStateShouldCompleteExceptionallyWhenUserHasNoUpdatePermission() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));

    // when
    final var future = manualModeServices.syncRuntimeState(authentication);

    // then
    assertServiceExceptionStatus(future, Status.FORBIDDEN);
    verify(backupApi, never()).syncMetadata(any());
  }

  @Test
  public void deleteRuntimeStateShouldDelegate() {
    // given
    when(backupApi.deleteRuntimeState(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = manualModeServices.deleteRuntimeState(authentication);

    // then
    assertThat(future).succeedsWithin(Duration.ofSeconds(1));
    verify(backupApi).deleteRuntimeState(PHYSICAL_TENANT_ID);
  }

  @Test
  public void deleteRuntimeStateShouldCompleteExceptionallyWhenUserHasNoUpdatePermission() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));

    // when
    final var future = manualModeServices.deleteRuntimeState(authentication);

    // then
    assertServiceExceptionStatus(future, Status.FORBIDDEN);
    verify(backupApi, never()).deleteRuntimeState(any());
  }

  private void assertServiceExceptionStatus(
      final CompletableFuture<?> future, final Status expectedStatus) {
    final var exception = catchThrowable(future::join).getCause();
    assertThat(exception).isInstanceOf(ServiceException.class);
    assertThat(((ServiceException) exception).getStatus()).isEqualTo(expectedStatus);
  }
}
