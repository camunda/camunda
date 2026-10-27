/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.discovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;

/** DNS discovery provider configuration. */
public class DynamicDiscoveryConfig extends NodeDiscoveryConfig {
  private Collection<String> addresses = new ArrayList<>();
  private Duration refreshInterval = Duration.ofSeconds(60);
  private int defaultPort = 26502;

  @Override
  public NodeDiscoveryProvider.Type getType() {
    return DynamicDiscoveryProvider.TYPE;
  }

  /**
   * Returns the list of DNS addresses to resolve.
   *
   * @return the list of DNS addresses
   */
  public Collection<String> getAddresses() {
    return addresses;
  }

  /**
   * Sets the DNS addresses.
   *
   * @param addresses the DNS addresses in format "host:port" or "host"
   * @return the DNS discovery configuration
   */
  public DynamicDiscoveryConfig setAddresses(final Collection<String> addresses) {
    this.addresses = addresses;
    return this;
  }

  /**
   * Returns the DNS refresh interval.
   *
   * @return the refresh interval
   */
  public Duration getRefreshInterval() {
    return refreshInterval;
  }

  /**
   * Sets the DNS refresh interval.
   *
   * @param refreshInterval the interval at which to refresh DNS resolutions
   * @return the DNS discovery configuration
   */
  public DynamicDiscoveryConfig setRefreshInterval(final Duration refreshInterval) {
    this.refreshInterval = refreshInterval;
    return this;
  }

  public int getDefaultPort() {
    return defaultPort;
  }

  public void setDefaultPort(final int defaultPort) {
    this.defaultPort = defaultPort;
  }
}
