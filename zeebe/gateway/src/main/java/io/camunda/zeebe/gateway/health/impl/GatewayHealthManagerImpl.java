/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.health.impl;

import io.camunda.zeebe.gateway.health.GatewayHealthManager;
import io.camunda.zeebe.gateway.health.Status;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.grpc.BindableService;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public final class GatewayHealthManagerImpl implements GatewayHealthManager {
  private static final Set<String> MONITORED_SERVICES =
      Set.of(GatewayGrpc.SERVICE_NAME, HealthStatusManager.SERVICE_NAME_ALL_SERVICES);

  private final HealthStatusManager statusManager;
  private final AtomicReference<Status> status = new AtomicReference<>();

  public GatewayHealthManagerImpl() {
    this(new HealthStatusManager());
  }

  public GatewayHealthManagerImpl(final HealthStatusManager statusManager) {
    this.statusManager = statusManager;
    setStatus(Status.INITIAL);
  }

  @Override
  public Status getStatus() {
    return status.get();
  }

  @Override
  public void setStatus(final Status status) {
    final var oldStatus = this.status.getAndAccumulate(status, this::computeStatus);

    if (oldStatus != Status.SHUTDOWN && oldStatus != status) {
      updateGrpcHealthStatus(status);
    }
  }

  @Override
  public BindableService getHealthService() {
    return statusManager.getHealthService();
  }

  private Status computeStatus(final Status currentStatus, final Status newStatus) {
    if (currentStatus == Status.SHUTDOWN) {
      return Status.SHUTDOWN;
    }

    return newStatus;
  }

  private void updateGrpcHealthStatus(final Status status) {
    switch (status) {
      case RUNNING:
        setGrpcHealthStatus(ServingStatus.SERVING);
        break;
      case SHUTDOWN:
        statusManager.enterTerminalState();
        break;
      case INITIAL:
      case STARTING:
      default:
        setGrpcHealthStatus(ServingStatus.NOT_SERVING);
        break;
    }
  }

  private void setGrpcHealthStatus(final ServingStatus servingStatus) {
    MONITORED_SERVICES.forEach(service -> statusManager.setStatus(service, servingStatus));
  }
}
