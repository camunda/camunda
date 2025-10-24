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

public class CommandApi {
  private static final String PREFIX = "camunda.cluster.network.command-api";
  private static final Set<String> LEGACY_HOST_PROPERTIES =
      Set.of("zeebe.broker.network.commandApi.host");
  private static final Set<String> LEGACY_PORT_PROPERTIES =
      Set.of("zeebe.broker.network.commandApi.port");
  private static final Set<String> LEGACY_ADVERTISED_HOST_PROPERTIES =
      Set.of("zeebe.broker.network.commandApi.advertisedHost");
  private static final Set<String> LEGACY_ADVERTISED_PORT_PROPERTIES =
      Set.of("zeebe.broker.network.commandApi.advertisedPort");

  /** Overrides the host used for gateway-to-broker communication */
  private String host;

  /** Sets the port used for gateway-to-broker communication */
  private Integer port;

  /**
   * Controls the advertised host. This is particularly useful if your broker stands behind a proxy.
   * If omitted, defaults to:
   *
   * <ul>
   *   <li>If zeebe.broker.network.commandApi.host was set, use this.
   *   <li>Use the resolved value of zeebe.broker.network.advertisedHost
   * </ul>
   */
  private String advertisedHost;

  /**
   * Controls the advertised port; if omitted defaults to the port. This is particularly useful if
   * your broker stands behind a proxy.
   */
  private Integer advertisedPort;

  public String getHost() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".host",
        host,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_HOST_PROPERTIES);
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public Integer getPort() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".port",
        port,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_PORT_PROPERTIES);
  }

  public void setPort(final Integer port) {
    this.port = port;
  }

  public String getAdvertisedHost() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".advertised-host",
        advertisedHost,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ADVERTISED_HOST_PROPERTIES);
  }

  public void setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
  }

  public Integer getAdvertisedPort() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".advertised-port",
        advertisedPort,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ADVERTISED_PORT_PROPERTIES);
  }

  public void setAdvertisedPort(final Integer advertisedPort) {
    this.advertisedPort = advertisedPort;
  }
}
