/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "exporting")
public final class ExportingEndpoint {
  static final String PAUSE = "pause";
  static final String RESUME = "resume";
  private final ExportingStateController exportingStateController;
  private final PhysicalTenantIds physicalTenantIds;

  @Autowired
  public ExportingEndpoint(
      final ExportingStateController exportingStateController,
      final PhysicalTenantIds physicalTenantIds) {
    this.exportingStateController = exportingStateController;
    this.physicalTenantIds = physicalTenantIds;
  }

  ExportingEndpoint(final ExportingStateController exportingStateController) {
    this(exportingStateController, PhysicalTenantIds.DEFAULT);
  }

  /**
   * Pauses or resumes exporting. Without a {@code physicalTenant} query parameter every physical
   * tenant is paused or resumed, keeping the whole-cluster meaning the operation always had. With
   * the parameter, only the given physical tenant is affected.
   *
   * <p>Exporting is paused per tenant, so a fan-out that fails halfway leaves the cluster with a
   * mix of paused and exporting tenants. Rather than hide that, every tenant is attempted and the
   * first failure decides the response — the operator retries, and a repeated pause or resume of an
   * already-paused or already-exporting tenant is a no-op. {@link PhysicalTenantFanOut} is what
   * makes "every tenant is attempted" true even for a tenant that fails before returning a future.
   */
  @WriteOperation
  public WebEndpointResponse<?> post(
      @Selector final String operationKey,
      final @Nullable Boolean soft,
      final @Nullable String physicalTenant) {

    final List<String> targets;
    try {
      targets = PhysicalTenantScope.resolve(physicalTenant, physicalTenantIds);
    } catch (final UnknownPhysicalTenantException e) {
      return new WebEndpointResponse<>(e.getMessage(), WebEndpointResponse.STATUS_BAD_REQUEST);
    }

    final var failure =
        PhysicalTenantFanOut.firstFailure(
            PhysicalTenantFanOut.over(
                targets, tenant -> apply(operationKey, Boolean.TRUE.equals(soft), tenant)));
    if (failure == null) {
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
    }
    final var message =
        failure.getCause() != null ? failure.getCause().getMessage() : failure.getMessage();
    return new WebEndpointResponse<>(message, WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
  }

  private CompletableFuture<Void> apply(
      final String operationKey, final boolean soft, final String physicalTenantId) {
    final var tenant = exportingStateController.getByTenant(physicalTenantId);
    return switch (operationKey) {
      case RESUME -> tenant.resumeExporting();
      case PAUSE -> soft ? tenant.softPauseExporting() : tenant.pauseExporting();
      default -> throw new UnsupportedOperationException();
    };
  }
}
