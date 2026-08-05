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
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.gateway.admin.ExportingStatus;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * Exposes exporting pause/resume for a single physical tenant, authorizing the caller explicitly
 * (ADR 002 D2) since these operations are not engine commands.
 *
 * <p>It extends {@link PhysicalTenantScopedApiServices} for uniformity with the other
 * explicitly-checked management services ({@code DocumentServices}, {@code SecretServices}) and to
 * carry the physical tenant id, which it passes to the {@link ExportingStateController}. That
 * controller — shared with the {@code ExportingEndpoint} actuator — owns how the scope is applied,
 * so the base class's {@code brokerRequestMutators()} PT-stamping is not exercised here.
 */
@NullMarked
public final class ExportingServices extends PhysicalTenantScopedApiServices<ExportingServices> {

  private final ExportingStateController exportingStateController;
  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;

  public ExportingServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ExportingStateController exportingStateController,
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
                ? exportingStateController.softPauseExporting(getPhysicalTenantId())
                : exportingStateController.pauseExporting(getPhysicalTenantId()));
  }

  public CompletableFuture<Void> resumeExporting(final CamundaAuthentication authentication) {
    return withExporterPausePermission(
        authentication, () -> exportingStateController.resumeExporting(getPhysicalTenantId()));
  }

  /**
   * Returns the exporting status aggregated over every partition replica in the cluster
   * configuration, so backup tooling can confirm a pause took effect instead of relying on its own
   * bookkeeping.
   *
   * <p>Reading is gated on the same permission as pause/resume because {@code EXPORTER} only
   * defines {@code PAUSE}: there is no separate read permission to grant.
   */
  public CompletableFuture<ExportingStatus> getExportingStatus(
      final CamundaAuthentication authentication) {
    return withExporterPausePermission(authentication, this::queryExportingStatus);
  }

  private CompletableFuture<ExportingStatus> queryExportingStatus() {
    return requestSender
        .getTopology()
        .thenApply(
            topology -> {
              if (topology.isLeft()) {
                final var error = topology.getLeft();
                throw new IllegalStateException(error.code() + ": " + error.message());
              }
              return aggregateStatus(topology.get());
            });
  }

  /**
   * Exporting state is stored per partition replica, so replicas can disagree mid-rollout; {@link
   * ExportingStatus#aggregate(java.util.Set)} folds them into a single phase, or {@code MIXED} when
   * they don't agree. {@link ExportingState#UNKNOWN} means the replica has never been touched by a
   * state-change operation, which is equivalent to actively exporting.
   */
  private static ExportingStatus aggregateStatus(final ClusterConfiguration configuration) {
    final var phases =
        configuration.members().values().stream()
            .flatMap(member -> member.partitions().values().stream())
            .map(partition -> partition.config().exporting().state())
            .map(state -> state == ExportingState.UNKNOWN ? ExportingState.EXPORTING : state)
            .map(Enum::name)
            .collect(Collectors.toSet());
    return ExportingStatus.aggregate(phases);
  }

  /**
   * Runs {@code action} only if the caller holds the {@code EXPORTER/PAUSE} permission, mapping
   * failures to {@link io.camunda.service.exception.ServiceException} so they surface with the
   * correct status.
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
