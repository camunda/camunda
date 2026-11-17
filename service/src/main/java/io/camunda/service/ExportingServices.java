/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.EXPORTER_PAUSE_AUTHORIZATION;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController;
import io.camunda.zeebe.dynamic.config.api.ExportingStatus;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

/**
 * Exposes exporting pause/resume for a single physical tenant, authorizing the caller explicitly
 * (ADR 002 D2) since these operations are not engine commands.
 *
 * <p>It extends {@link PhysicalTenantScopedApiServices} for uniformity with the other
 * explicitly-checked management services ({@code DocumentServices}, {@code SecretServices}) and to
 * carry the physical tenant id, but it only consumes {@link #getPhysicalTenantId()}: the actual
 * state change is submitted through dynamic cluster configuration by {@link
 * ExportingStateController.ByTenant}, which was resolved for this tenant, so the base class's
 * {@code brokerRequestMutators()} PT-stamping is not exercised here.
 */
@NullMarked
public final class ExportingServices extends PhysicalTenantScopedApiServices<ExportingServices> {

  private final ExportingStateController.ByTenant exportingStateController;
  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;

  public ExportingServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ExportingStateController.ByTenant exportingStateController,
      final AuthorizationChecker authorizationChecker,
      final AuthorizationsConfiguration authorizationsConfig,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.exportingStateController = exportingStateController;
    this.authorizationChecker = authorizationChecker;
    this.authorizationsConfig = authorizationsConfig;
  }

  public CompletableFuture<Void> pauseExporting(
      final boolean soft, final CamundaAuthentication authentication) {
    return withExporterPausePermission(
        authentication,
        () ->
            soft
                ? exportingStateController.softPauseExporting()
                : exportingStateController.pauseExporting());
  }

  public CompletableFuture<Void> resumeExporting(final CamundaAuthentication authentication) {
    return withExporterPausePermission(authentication, exportingStateController::resumeExporting);
  }

  /**
   * Returns the exporting status aggregated over all partitions of the physical tenant, so backup
   * tooling can confirm a pause took effect instead of relying on its own bookkeeping.
   *
   * <p>Reading is gated on the same permission as pause/resume because {@code EXPORTER} only
   * defines {@code PAUSE}: there is no separate read permission to grant.
   */
  public CompletableFuture<ExportingStatus> getExportingStatus(
      final CamundaAuthentication authentication) {
    return withExporterPausePermission(
        authentication, exportingStateController::getExportingStatus);
  }

  /**
   * Runs {@code action} only if the caller holds the {@code EXPORTER/PAUSE} permission, mapping
   * failures from both the synchronous and asynchronous phases to {@link
   * io.camunda.service.exception.ServiceException} so they surface with the correct status. The
   * change is submitted synchronously but resolves once the coordinator applies it, so the returned
   * future may also complete exceptionally — both paths must be mapped.
   *
   * <p>Resume is gated on the same permission as pause: {@code EXPORTER} only defines {@code
   * PAUSE}, and a caller allowed to stop exporting must also be able to start it again — otherwise
   * they could leave the cluster in a paused state they cannot undo.
   */
  private <T> CompletableFuture<T> withExporterPausePermission(
      final CamundaAuthentication authentication, final Supplier<CompletableFuture<T>> action) {
    if (!hasExporterPausePermission(authentication)) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(EXPORTER_PAUSE_AUTHORIZATION));
    }

    try {
      return action
          .get()
          .exceptionallyCompose(e -> CompletableFuture.failedFuture(ErrorMapper.mapError(e)));
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(e));
    }
  }

  private boolean hasExporterPausePermission(final CamundaAuthentication authentication) {
    if (!authorizationsConfig.isEnabled()) {
      return true;
    }

    return authorizationChecker
        .collectPermissionTypes(
            AuthorizationScope.WILDCARD_CHAR,
            EXPORTER_PAUSE_AUTHORIZATION.resourceType(),
            authentication)
        .contains(EXPORTER_PAUSE_AUTHORIZATION.permissionType());
  }
}
