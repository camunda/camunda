/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_HOST;

import java.util.Map;
import java.util.Set;

/** Network configuration for cluster communication. */
public class Network {

  private static final String PREFIX = "camunda.cluster.network";

  private static final Map<String, String> LEGACY_GATEWAY_NETWORK_PROPERTIES =
      Map.of("host", "zeebe.gateway.cluster.host");
  private static final Map<String, String> LEGACY_BROKER_NETWORK_PROPERTIES =
      Map.of("host", "zeebe.broker.network.host");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_NETWORK_PROPERTIES;

  /** The network host for internal cluster communication. */
  private String host = DEFAULT_CLUSTER_HOST;

  public String getHost() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".host",
        host,
        String.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("host")));
  }

  public void setHost(final String host) {
    this.host = host;
  }

  @Override
  public Network clone() {
    final Network copy = new Network();
    copy.host = host;

    return copy;
  }

  public Network withBrokerNetworkProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_NETWORK_PROPERTIES;
    return copy;
  }

  public Network withGatewayNetworkProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_NETWORK_PROPERTIES;
    return copy;
  }
}
