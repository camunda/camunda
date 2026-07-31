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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RecoveryServices extends PhysicalTenantScopedApiServices<RecoveryServices> {

  private static final List<RequiredAuthorization<Object>> MODE_CHANGE_AUTHORIZATIONS =
      List.of(SYSTEM_UPDATE_AUTHORIZATION, BACKUP_RESTORE_AUTHORIZATION);

  private final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender;
  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;

  public RecoveryServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender,
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
    this.clusterConfigurationRequestSender = clusterConfigurationRequestSender;
    this.authorizationChecker = authorizationChecker;
    this.authorizationsConfig = authorizationsConfig;
  }

  public CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> changeMode(
      final Mode mode, final boolean dryRun, final CamundaAuthentication authentication) {
    if (MODE_CHANGE_AUTHORIZATIONS.stream().noneMatch(a -> hasPermission(a, authentication))) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(MODE_CHANGE_AUTHORIZATIONS.getFirst()));
    }

    return clusterConfigurationRequestSender.modeChange(
        new ModeChangeRequest(getPhysicalTenantId(), mode, dryRun));
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
