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
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.admin.ExportingRequestBroadcaster;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class ExportingServices extends PhysicalTenantScopedApiServices<ExportingServices> {

  private final ExportingRequestBroadcaster exportingRequestBroadcaster;
  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationsConfiguration authorizationsConfig;

  public ExportingServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ExportingRequestBroadcaster exportingRequestBroadcaster,
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
    this.exportingRequestBroadcaster = exportingRequestBroadcaster;
    this.authorizationChecker = authorizationChecker;
    this.authorizationsConfig = authorizationsConfig;
  }

  public CompletableFuture<Void> pauseExporting(
      final boolean soft, final CamundaAuthentication authentication) {
    return withSystemUpdatePermission(
        authentication,
        () ->
            soft
                ? exportingRequestBroadcaster.softPauseExporting(getPhysicalTenantId())
                : exportingRequestBroadcaster.pauseExporting(getPhysicalTenantId()));
  }

  public CompletableFuture<Void> resumeExporting(final CamundaAuthentication authentication) {
    return withSystemUpdatePermission(
        authentication, () -> exportingRequestBroadcaster.resumeExporting(getPhysicalTenantId()));
  }

  /**
   * Runs {@code action} only if the caller holds the {@code SYSTEM/UPDATE} permission, mapping
   * failures from both the synchronous and asynchronous phases to {@link
   * io.camunda.service.exception.ServiceException} so they surface with the correct status. The
   * broadcaster validates topology synchronously (throwing before it returns a future) but
   * dispatches the broker requests asynchronously, so the returned future may also complete
   * exceptionally — both paths must be mapped.
   */
  private CompletableFuture<Void> withSystemUpdatePermission(
      final CamundaAuthentication authentication, final Supplier<CompletableFuture<Void>> action) {
    if (!hasSystemUpdatePermission(authentication)) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(
              RequiredAuthorization.of(a -> a.system().permissionType(PermissionType.UPDATE))));
    }

    try {
      return action
          .get()
          .exceptionallyCompose(e -> CompletableFuture.failedFuture(ErrorMapper.mapError(e)));
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
