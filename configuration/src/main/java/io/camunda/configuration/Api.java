/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Api {

  private static final String PREFIX = "camunda.api";
  private static final Set<String> LEGACY_ENABLED_PROPERTIES =
      Set.of("zeebe.broker.gateway.enable");

  /** Configuration for long-polling behavior */
  @NestedConfigurationProperty private LongPolling longPolling = new LongPolling();

  /** Configuration for grpc behavior */
  @NestedConfigurationProperty private Grpc grpc = new Grpc();

  /** Configuration for rest behavior */
  @NestedConfigurationProperty private Rest rest = new Rest();

  /**
   * Enables the gateway (gRPC and REST API) that is embedded in the broker process. Only relevant
   * when running as a broker with an embedded gateway; standalone gateway processes are always
   * enabled by virtue of being started.
   */
  private boolean enabled = true;

  public LongPolling getLongPolling() {
    return longPolling;
  }

  public void setLongPolling(final LongPolling longPolling) {
    this.longPolling = longPolling;
  }

  public Grpc getGrpc() {
    return grpc;
  }

  public void setGrpc(final Grpc grpc) {
    this.grpc = grpc;
  }

  public Rest getRest() {
    return rest;
  }

  public void setRest(final Rest rest) {
    this.rest = rest;
  }

  public boolean isEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".enabled",
        enabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLED_PROPERTIES);
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }
}
