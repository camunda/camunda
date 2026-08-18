/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.admin.ExportingRequestBroadcaster;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@RestControllerEndpoint(id = "exporting")
public final class ExportingEndpoint {
  static final String PAUSE = "pause";
  static final String RESUME = "resume";
  private final ExportingRequestBroadcaster exportingRequestBroadcaster;
  private final PhysicalTenantIds physicalTenantIds;

  @Autowired
  public ExportingEndpoint(
      final BrokerClient brokerClient, final PhysicalTenantIds physicalTenantIds) {
    this(new ExportingRequestBroadcaster(brokerClient), physicalTenantIds);
  }

  ExportingEndpoint(final ExportingRequestBroadcaster exportingRequestBroadcaster) {
    this(exportingRequestBroadcaster, PhysicalTenantIds.DEFAULT);
  }

  ExportingEndpoint(
      final ExportingRequestBroadcaster exportingRequestBroadcaster,
      final PhysicalTenantIds physicalTenantIds) {
    this.exportingRequestBroadcaster = exportingRequestBroadcaster;
    this.physicalTenantIds = physicalTenantIds;
  }

  /**
   * Pauses or resumes exporting. Without a {@code physicalTenant} query parameter every physical
   * tenant is paused or resumed, keeping the whole-cluster meaning the operation always had. With
   * the parameter, only the given physical tenant is affected.
   *
   * <p>Exporting is paused per tenant, so a fan-out that fails halfway leaves the cluster with a
   * mix of paused and exporting tenants. Rather than hide that, every tenant is attempted and the
   * first failure decides the response — the operator retries, and a repeated pause or resume of an
   * already-paused or already-exporting tenant is a no-op.
   */
  @PostMapping(path = "/{operationKey}")
  public WebEndpointResponse<?> post(
      @PathVariable("operationKey") final String operationKey,
      @RequestParam(defaultValue = "false") final boolean soft,
      @RequestParam(required = false) final @Nullable String physicalTenant) {

    final List<String> targets;
    try {
      targets = PhysicalTenantScope.resolve(physicalTenant, physicalTenantIds);
    } catch (final UnknownPhysicalTenantException e) {
      return new WebEndpointResponse<>(e.getMessage(), WebEndpointResponse.STATUS_BAD_REQUEST);
    }

    try {
      final var results =
          targets.stream().map(tenant -> apply(operationKey, soft, tenant)).toList();
      // collected first so every tenant is attempted even when an earlier one already failed
      CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).join();
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
    } catch (final CompletionException e) {
      final var message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      return new WebEndpointResponse<>(message, WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(
          e.getMessage(), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }

  private CompletableFuture<Void> apply(
      final String operationKey, final boolean soft, final String physicalTenantId) {
    return switch (operationKey) {
      case RESUME -> exportingRequestBroadcaster.resumeExporting(physicalTenantId);
      case PAUSE ->
          soft
              ? exportingRequestBroadcaster.softPauseExporting(physicalTenantId)
              : exportingRequestBroadcaster.pauseExporting(physicalTenantId);
      default -> throw new UnsupportedOperationException();
    };
  }
}
