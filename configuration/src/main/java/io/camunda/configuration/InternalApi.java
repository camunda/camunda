/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.broker.system.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults;
import java.util.Map;
import java.util.Set;

public class InternalApi implements Cloneable {
  private static final String PREFIX = "camunda.cluster.network.internal-api";

  private static final Map<String, String> LEGACY_GATEWAY_NETWORK_INTERNAL_API_PROPERTIES =
      // "host" and "advertisedHost" are intentionally empty because the legacy values are not
      // resolved here. They are considered when accessing Network.getHost() and
      // Network.getAdvertisedHost() in GatewayBasePropertiesOverride.
      Map.of(
          "host", "",
          "port", "zeebe.gateway.cluster.port",
          "advertisedHost", "",
          "advertisedPort", "zeebe.gateway.cluster.advertisedPort");

  private static final Map<String, String> LEGACY_BROKER_NETWORK_INTERNAL_API_PROPERTIES =
      Map.of(
          "host", "zeebe.broker.network.internalApi.host",
          "port", "zeebe.broker.network.internalApi.port",
          "advertisedHost", "zeebe.broker.network.internalApi.advertisedHost",
          "advertisedPort", "zeebe.broker.network.internalApi.advertisedPort");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_NETWORK_INTERNAL_API_PROPERTIES;

  /** Overrides the host used for internal broker-to-broker communication */
  private String host;

  /** Sets the port used for internal broker-to-broker communication */
  private Integer port;

  /**
   * Controls the advertised host. This is particularly useful if your broker stands behind a proxy.
   *
   * <p>If omitted, defaults to:
   *
   * <ul>
   *   <li>If zeebe.broker.network.internalApi.host was set, use this.
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
        Set.of(legacyPropertiesMap.get("host")));
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
        Set.of(legacyPropertiesMap.get("port")));
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
        Set.of(legacyPropertiesMap.get("advertisedHost")));
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
        Set.of(legacyPropertiesMap.get("advertisedPort")));
  }

  public void setAdvertisedPort(final Integer advertisedPort) {
    this.advertisedPort = advertisedPort;
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (final CloneNotSupportedException e) {
      throw new AssertionError("Unexpected: Class must implement Cloneable", e);
    }
  }

  public InternalApi withBrokerInternalApiProperties() {
    final var copy = (InternalApi) clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_NETWORK_INTERNAL_API_PROPERTIES;
    copy.port = copy.port == null ? NetworkCfg.DEFAULT_INTERNAL_API_PORT : copy.port;
    return copy;
  }

  public InternalApi withGatewayInternalApiProperties() {
    final var copy = (InternalApi) clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_NETWORK_INTERNAL_API_PROPERTIES;
    copy.port = copy.port == null ? ConfigurationDefaults.DEFAULT_CLUSTER_PORT : copy.port;
    return copy;
  }
}
