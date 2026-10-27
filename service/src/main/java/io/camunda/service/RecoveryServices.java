/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.BACKUP_RESTORE_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.SYSTEM_UPDATE_AUTHORIZATION;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RecoveryServices extends PhysicalTenantScopedApiServices<RecoveryServices> {

  private static final List<RequiredAuthorization<Object>> MODE_CHANGE_AUTHORIZATIONS =
      List.of(BACKUP_RESTORE_AUTHORIZATION, SYSTEM_UPDATE_AUTHORIZATION);
  private static final List<RequiredAuthorization<Object>> RESTORE_AUTHORIZATIONS =
      List.of(BACKUP_RESTORE_AUTHORIZATION);

  private final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender;
  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;
  private final TenantRestoreEnvironment tenantRestoreEnvironment;

  public RecoveryServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender,
      final AuthorizationChecker authorizationChecker,
      final AuthorizationsConfiguration authorizationsConfig,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final TenantRestoreEnvironment tenantRestoreEnvironment) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.clusterConfigurationRequestSender = clusterConfigurationRequestSender;
    this.authorizationChecker = authorizationChecker;
    this.authorizationsConfig = authorizationsConfig;
    this.tenantRestoreEnvironment = tenantRestoreEnvironment;
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> changeMode(
      final Mode mode, final boolean dryRun, final CamundaAuthentication authentication) {
    return withAnyPermission(
        MODE_CHANGE_AUTHORIZATIONS,
        authentication,
        () ->
            clusterConfigurationRequestSender.modeChange(
                new ModeChangeRequest(Optional.of(getPhysicalTenantId()), mode, dryRun)));
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> restore(
      final RestoreParameters parameters,
      final boolean dryRun,
      final CamundaAuthentication authentication) {
    return withAnyPermission(
        RESTORE_AUTHORIZATIONS,
        authentication,
        () -> clusterConfigurationRequestSender.restore(toRestoreRequest(parameters, dryRun)));
  }

  private RestoreRequest toRestoreRequest(
      final RestoreParameters parameters, final boolean dryRun) {
    return new RestoreRequest(
        getPhysicalTenantId(),
        new TenantRestoreArguments(
            parameters,
            tenantRestoreEnvironment.databaseType(),
            tenantRestoreEnvironment.continuousBackups()),
        dryRun);
  }

  public CompletableFuture<Either<ErrorResponse, CurrentClusterConfiguration>> restoreStatus(
      final CamundaAuthentication authentication) {
    return withAnyPermission(
        RESTORE_AUTHORIZATIONS, authentication, clusterConfigurationRequestSender::getTopology);
  }

  private <T> CompletableFuture<T> withAnyPermission(
      final List<RequiredAuthorization<Object>> requiredAuthorizations,
      final CamundaAuthentication authentication,
      final Supplier<CompletableFuture<T>> operation) {
    final var missingAuthorizations =
        requiredAuthorizations.stream()
            .filter(authorization -> !hasPermission(authorization, authentication))
            .toList();
    if (missingAuthorizations.size() == requiredAuthorizations.size()) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(missingAuthorizations));
    }

    return operation.get();
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
