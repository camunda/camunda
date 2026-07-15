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
import io.camunda.service.admin.exporting.ExportingControlApi;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.concurrent.CompletableFuture;

public final class ExportingServices extends PhysicalTenantScopedApiServices<ExportingServices> {

  private final ExportingControlApi exportingControlApi;
  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;

  public ExportingServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ExportingControlApi exportingControlApi,
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
    this.exportingControlApi = exportingControlApi;
    this.authorizationChecker = authorizationChecker;
    this.authorizationsConfig = authorizationsConfig;
  }

  public CompletableFuture<Void> pauseExporting(
      final boolean soft, final CamundaAuthentication authentication) {
    if (!hasSystemUpdatePermission(authentication)) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(
              RequiredAuthorization.of(a -> a.system().permissionType(PermissionType.UPDATE))));
    }

    try {
      return soft
          ? exportingControlApi.softPauseExporting(getPhysicalTenantId())
          : exportingControlApi.pauseExporting(getPhysicalTenantId());
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(e));
    }
  }

  public CompletableFuture<Void> resumeExporting(final CamundaAuthentication authentication) {
    if (!hasSystemUpdatePermission(authentication)) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(
              RequiredAuthorization.of(a -> a.system().permissionType(PermissionType.UPDATE))));
    }

    try {
      return exportingControlApi.resumeExporting(getPhysicalTenantId());
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(e));
    }
  }

  private boolean hasSystemUpdatePermission(final CamundaAuthentication authentication) {
    if (!authorizationsConfig.isEnabled()) {
      return true;
    }

    return authorizationChecker
        .collectPermissionTypes(
            AuthorizationScope.WILDCARD_CHAR, AuthorizationResourceType.SYSTEM, authentication)
        .contains(PermissionType.UPDATE);
  }
}
