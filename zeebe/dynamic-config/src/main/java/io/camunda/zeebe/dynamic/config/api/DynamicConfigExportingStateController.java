/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Applies exporting state changes through dynamic cluster configuration, so that a pause survives
 * restarts and applies to partitions that join later.
 *
 * <p>The change is cluster-wide: dynamic configuration stores the exporting state per partition
 * replica, with no notion of physical tenants, so {@code physicalTenantId} is accepted but ignored.
 * This is the single place to scope the change once dynamic configuration models physical tenants.
 *
 * <p>The poll interval and timeout used while awaiting the change are configurable so tests do not
 * have to wait on production defaults.
 */
@NullMarked
public final class DynamicConfigExportingStateController implements ExportingStateController {

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private final ClusterConfigurationManagementRequestSender requestSender;
  private final ClusterConfigurationChangeAwaiter changeAwaiter;

  public DynamicConfigExportingStateController(
      final ClusterConfigurationManagementRequestSender requestSender) {
    this(requestSender, POLL_INTERVAL, TIMEOUT);
  }

  public DynamicConfigExportingStateController(
      final ClusterConfigurationManagementRequestSender requestSender,
      final Duration pollInterval,
      final Duration timeout) {
    this.requestSender = requestSender;
    changeAwaiter = new ClusterConfigurationChangeAwaiter(requestSender, pollInterval, timeout);
  }

  @Override
  public CompletableFuture<Void> pauseExporting(final String physicalTenantId) {
    return changeState(ExportingState.PAUSED);
  }

  @Override
  public CompletableFuture<Void> softPauseExporting(final String physicalTenantId) {
    return changeState(ExportingState.SOFT_PAUSED);
  }

  @Override
  public CompletableFuture<Void> resumeExporting(final String physicalTenantId) {
    return changeState(ExportingState.EXPORTING);
  }

  private CompletableFuture<Void> changeState(final ExportingState targetState) {
    return changeAwaiter.awaitCompletion(
        requestSender.changeExportingState(new ExportingStateChangeRequest(targetState, false)));
  }
}
