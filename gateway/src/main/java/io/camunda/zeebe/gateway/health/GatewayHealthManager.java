/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.health;

import io.grpc.BindableService;

public interface GatewayHealthManager {

  /**
   * This method returns the latest health status for the gateway, and is thread safe.
   *
   * @return the current health status
   */
  Status getStatus();

  /**
   * This method sets the health status in a thread safe way.
   *
   * @param status the new health status of the gateway
   */
  void setStatus(final Status status);

  /**
   * This method return the GRPC {@link BindableService} to use it in the {@link
   * io.grpc.ServerBuilder }
   *
   * @return the bindable GRPC service
   */
  BindableService getHealthService();
}
