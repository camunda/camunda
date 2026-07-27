/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class RuntimeBackupServices
    extends PhysicalTenantScopedApiServices<RuntimeBackupServices> {

  private final BackupApi backupApi;
  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;
  private final boolean backupIdGenerated;

  public RuntimeBackupServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final BackupApi backupApi,
      final AuthorizationChecker authorizationChecker,
      final AuthorizationsConfiguration authorizationsConfig,
      final boolean backupIdGenerated,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.backupApi = backupApi;
    this.authorizationChecker = authorizationChecker;
    this.authorizationsConfig = authorizationsConfig;
    this.backupIdGenerated = backupIdGenerated;
  }

  public CompletableFuture<Long> takeBackup(
      @Nullable final Long backupId, final CamundaAuthentication authentication) {
    if (!hasPermission(PermissionType.UPDATE, authentication)) {
      return failedFuture(PermissionType.UPDATE);
    }

    if (backupIdGenerated) {
      if (backupId != null) {
        return CompletableFuture.failedFuture(
            new ServiceException(
                "Cannot take backup with an explicit backupId when continuous backups and/or a "
                    + "backup or checkpoint schedule is enabled for this physical tenant. Take a "
                    + "backup without specifying a backupId.",
                ServiceException.Status.INVALID_ARGUMENT));
      }
      return mapErrors(() -> backupApi.takeBackup(getPhysicalTenantId()).toCompletableFuture());
    } else {
      if (backupId == null || backupId <= 0) {
        return CompletableFuture.failedFuture(
            new ServiceException(
                "A backupId must be provided and it must be > 0",
                ServiceException.Status.INVALID_ARGUMENT));
      }

      return mapErrors(
          () -> backupApi.takeBackup(getPhysicalTenantId(), backupId).toCompletableFuture());
    }
  }

  public CompletableFuture<BackupStatus> getBackupStatus(
      final long backupId, final CamundaAuthentication authentication) {
    if (!hasPermission(PermissionType.READ, authentication)) {
      return failedFuture(PermissionType.READ);
    }

    return mapErrors(
            () -> backupApi.getStatus(getPhysicalTenantId(), backupId).toCompletableFuture())
        .thenCompose(
            status -> {
              if (status.status() == State.DOES_NOT_EXIST) {
                return CompletableFuture.failedFuture(
                    new ServiceException(
                        "Backup with id %d does not exist".formatted(backupId),
                        ServiceException.Status.NOT_FOUND));
              }
              return CompletableFuture.completedFuture(status);
            });
  }

  public CompletableFuture<List<BackupStatus>> listBackups(
      final @Nullable String prefix, final CamundaAuthentication authentication) {
    if (!hasPermission(PermissionType.READ, authentication)) {
      return failedFuture(PermissionType.READ);
    }

    if (prefix != null && !prefix.endsWith(BackupApi.WILDCARD)) {
      return CompletableFuture.failedFuture(
          new ServiceException(
              "Expected a prefix ending with '*', but got '%s'".formatted(prefix),
              ServiceException.Status.INVALID_ARGUMENT));
    }

    return mapErrors(
        () ->
            backupApi
                .listBackups(getPhysicalTenantId(), prefix != null ? prefix : BackupApi.WILDCARD)
                .toCompletableFuture());
  }

  public CompletableFuture<Void> deleteBackup(
      final long backupId, final CamundaAuthentication authentication) {
    if (!hasPermission(PermissionType.UPDATE, authentication)) {
      return failedFuture(PermissionType.UPDATE);
    }

    return mapErrors(
        () -> backupApi.deleteBackup(getPhysicalTenantId(), backupId).toCompletableFuture());
  }

  /**
   * Fetches checkpoint state and backup ranges together. Unlike the {@code backupRuntime} actuator,
   * this fails the whole request if either sub-request fails, instead of silently returning an
   * empty section — a partial-failure section is indistinguishable from "nothing to report yet" and
   * could mislead an operator making delete/restore decisions.
   */
  public CompletableFuture<RuntimeBackupState> getRuntimeState(
      final CamundaAuthentication authentication) {
    if (!hasPermission(PermissionType.READ, authentication)) {
      return failedFuture(PermissionType.READ);
    }

    final var checkpointState =
        mapErrors(() -> backupApi.getCheckpointState(getPhysicalTenantId()).toCompletableFuture());
    final var ranges =
        mapErrors(() -> backupApi.getBackupRanges(getPhysicalTenantId()).toCompletableFuture());
    return checkpointState.thenCombine(ranges, RuntimeBackupState::new);
  }

  /**
   * Force-writes checkpoint/backup metadata to the backup store, then returns the updated state.
   */
  public CompletableFuture<RuntimeBackupState> syncRuntimeState(
      final CamundaAuthentication authentication) {
    if (!hasPermission(PermissionType.UPDATE, authentication)) {
      return failedFuture(PermissionType.UPDATE);
    }

    final var ranges =
        mapErrors(() -> backupApi.syncMetadata(getPhysicalTenantId()).toCompletableFuture());
    final var checkpointState =
        mapErrors(() -> backupApi.getCheckpointState(getPhysicalTenantId()).toCompletableFuture());
    return checkpointState.thenCombine(ranges, RuntimeBackupState::new);
  }

  public CompletableFuture<Void> deleteRuntimeState(final CamundaAuthentication authentication) {
    if (!hasPermission(PermissionType.UPDATE, authentication)) {
      return failedFuture(PermissionType.UPDATE);
    }

    return mapErrors(
        () -> backupApi.deleteRuntimeState(getPhysicalTenantId()).toCompletableFuture());
  }

  /**
   * Runs the given future-producing call and maps both synchronous throws (e.g. from topology
   * validation before any future is built) and asynchronous failures of the returned future to a
   * {@link ServiceException}, so {@code GatewayErrorMapper} can translate it to the right HTTP
   * status.
   */
  private <T> CompletableFuture<T> mapErrors(final Supplier<CompletableFuture<T>> call) {
    final CompletableFuture<T> future;
    try {
      future = call.get();
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(e));
    }
    return future.handle(
        (value, error) -> {
          if (error != null) {
            throw new CompletionException(ErrorMapper.mapError(error));
          }
          return value;
        });
  }

  private <T> CompletableFuture<T> failedFuture(final PermissionType permissionType) {
    return CompletableFuture.failedFuture(
        ErrorMapper.createForbiddenException(
            RequiredAuthorization.of(a -> a.system().permissionType(permissionType))));
  }

  private boolean hasPermission(
      final PermissionType permission, final CamundaAuthentication authentication) {
    if (!authorizationsConfig.isEnabled()) {
      return true;
    }

    return authorizationChecker
        .collectPermissionTypes(
            AuthorizationScope.WILDCARD_CHAR, AuthorizationResourceType.SYSTEM, authentication)
        .contains(permission);
  }

  public record RuntimeBackupState(
      CheckpointStateResponse checkpointState, BackupRangesResponse ranges) {}
}
