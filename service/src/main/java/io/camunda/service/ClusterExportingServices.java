/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController;
import io.camunda.zeebe.dynamic.config.api.ExportingStatus;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

/**
 * Pauses, resumes, and reports exporting status across every physical tenant of the cluster in one
 * call (ADR 003 D2), served by the cluster-admin security chain.
 *
 * <p>Unlike {@link ExportingServices}, this class does not extend {@link
 * PhysicalTenantScopedApiServices} and performs no {@code CamundaAuthentication}-based permission
 * check: the cluster-admin chain authenticates against an isolated credential set that produces no
 * {@code CamundaAuthentication}, so there is nothing to check {@code EXPORTER:PAUSE} against. The
 * chain itself is the gate.
 *
 * <p>The change is submitted as a single plan covering every physical tenant (see {@link
 * ExportingStateController#clusterWide()}), rather than as one independently submitted request per
 * tenant. The plan's member and tenant operations are still applied asynchronously, so a timed-out
 * or cancelled request can leave some scopes already changed while others remain pending.
 */
@NullMarked
public final class ClusterExportingServices {

  private final ExportingStateController.ClusterWide exportingStateController;

  public ClusterExportingServices(
      final ExportingStateController.ClusterWide exportingStateController) {
    this.exportingStateController = exportingStateController;
  }

  public CompletableFuture<Void> pauseExporting(final boolean soft) {
    return mapped(
        soft
            ? exportingStateController::softPauseExporting
            : exportingStateController::pauseExporting);
  }

  public CompletableFuture<Void> resumeExporting() {
    return mapped(exportingStateController::resumeExporting);
  }

  public CompletableFuture<ExportingStatus> getExportingStatus() {
    return mapped(exportingStateController::getExportingStatus);
  }

  /**
   * Maps both a synchronous validation failure and an asynchronous one (an exceptionally-completed
   * future) to a {@link io.camunda.service.exception.ServiceException}, mirroring {@code
   * ExportingServices}'s wrapper minus the permission check. Without this, both failure paths reach
   * {@code GatewayErrorMapper} as an unrecognized exception and become HTTP 500 instead of the
   * per-PT endpoint's 503.
   */
  private <T> CompletableFuture<T> mapped(final Supplier<CompletableFuture<T>> action) {
    try {
      return action
          .get()
          .exceptionallyCompose(e -> CompletableFuture.failedFuture(ErrorMapper.mapError(e)));
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(e));
    }
  }
}
