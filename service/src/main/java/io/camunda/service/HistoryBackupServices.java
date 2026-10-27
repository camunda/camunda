/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.BACKUP_CREATE_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.BACKUP_DELETE_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.BACKUP_READ_AUTHORIZATION;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.backup.HistoryBackupApi;
import io.camunda.service.backup.HistoryBackupRequests;
import io.camunda.service.backup.HistoryBackupState;
import io.camunda.service.backup.HistoryBackupTaken;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * History (secondary-storage snapshot) backup operations for a single physical tenant.
 *
 * <p>Deliberately not a {@code PhysicalTenantScopedApiServices}: that base class exists to tag
 * broker requests with a partition group, and history backups never reach the broker.
 *
 * <p>{@link HistoryBackupApi} is synchronous and blocks on secondary-storage round-trips, so every
 * call is offloaded to the shared API executor. Without that, {@code RequestExecutor} would hand
 * the already-completed future to the async servlet and a Tomcat thread would block for the whole
 * call.
 */
@NullMarked
public final class HistoryBackupServices {

  private final String physicalTenantId;
  private final HistoryBackupApi api;
  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;
  private final Executor executor;

  public HistoryBackupServices(
      final String physicalTenantId,
      final HistoryBackupApi api,
      final AuthorizationChecker authorizationChecker,
      final AuthorizationsConfiguration authorizationsConfig,
      final ApiServicesExecutorProvider executorProvider) {
    this.physicalTenantId = physicalTenantId;
    this.api = api;
    this.authorizationChecker = authorizationChecker;
    this.authorizationsConfig = authorizationsConfig;
    executor = executorProvider.getExecutor();
  }

  /**
   * @param backupId nullable because the request body may omit it; unlike runtime backups, history
   *     backups have no generated-id mode, so an absent id is a bad request rather than a signal
   */
  public CompletableFuture<HistoryBackupTaken> takeBackup(
      final @Nullable Long backupId, final CamundaAuthentication authentication) {
    if (!hasPermission(BACKUP_CREATE_AUTHORIZATION, authentication)) {
      return failedFuture(BACKUP_CREATE_AUTHORIZATION);
    }

    return validated(
        () -> {
          final var id = HistoryBackupRequests.requireValidBackupId(backupId);
          return callAsync(() -> api.takeBackup(physicalTenantId, id));
        });
  }

  public CompletableFuture<HistoryBackupState> getBackupState(
      final long backupId, final CamundaAuthentication authentication) {
    if (!hasPermission(BACKUP_READ_AUTHORIZATION, authentication)) {
      return failedFuture(BACKUP_READ_AUTHORIZATION);
    }

    return validated(
        () -> {
          final var id = HistoryBackupRequests.requireValidBackupId(backupId);
          return callAsync(() -> api.getBackupState(physicalTenantId, id));
        });
  }

  public CompletableFuture<List<HistoryBackupState>> listBackups(
      final @Nullable String prefix,
      final boolean verbose,
      final CamundaAuthentication authentication) {
    if (!hasPermission(BACKUP_READ_AUTHORIZATION, authentication)) {
      return failedFuture(BACKUP_READ_AUTHORIZATION);
    }

    return validated(
        () -> {
          final var queried = HistoryBackupRequests.requireValidPrefix(prefix);
          return callAsync(() -> api.getBackups(physicalTenantId, verbose, queried));
        });
  }

  public CompletableFuture<Void> deleteBackup(
      final long backupId, final CamundaAuthentication authentication) {
    if (!hasPermission(BACKUP_DELETE_AUTHORIZATION, authentication)) {
      return failedFuture(BACKUP_DELETE_AUTHORIZATION);
    }

    return validated(
        () -> {
          final var id = HistoryBackupRequests.requireValidBackupId(backupId);
          return callAsync(
              () -> {
                api.deleteBackup(physicalTenantId, id);
                return null;
              });
        });
  }

  /** Turns a rejection raised by {@link HistoryBackupRequests} into a failed future. */
  private static <T> CompletableFuture<T> validated(final Supplier<CompletableFuture<T>> request) {
    try {
      return request.get();
    } catch (final ServiceException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  /**
   * Runs a blocking port call on the shared API executor and maps any failure to a {@link
   * ServiceException}, so {@code GatewayErrorMapper} can translate it to the right HTTP status.
   */
  private <T> CompletableFuture<T> callAsync(final Supplier<T> call) {
    return CompletableFuture.supplyAsync(call, executor)
        .handle(
            (value, error) -> {
              if (error != null) {
                throw new CompletionException(ErrorMapper.mapError(error));
              }
              return value;
            });
  }

  private <T> CompletableFuture<T> failedFuture(
      final RequiredAuthorization<?> requiredAuthorization) {
    return CompletableFuture.failedFuture(
        ErrorMapper.createForbiddenException(requiredAuthorization));
  }

  private boolean hasPermission(
      final RequiredAuthorization<?> requiredAuthorization,
      final CamundaAuthentication authentication) {
    if (!authorizationsConfig.isEnabled()) {
      return true;
    }

    return authorizationChecker
        .collectPermissionTypes(
            AuthorizationScope.WILDCARD_CHAR, requiredAuthorization.resourceType(), authentication)
        .contains(requiredAuthorization.permissionType());
  }
}
