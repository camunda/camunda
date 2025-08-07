/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.discovery;

import java.time.Duration;

/** Kubernetes headless service discovery configuration. */
public class KubernetesDiscoveryConfig extends NodeDiscoveryConfig {
  private String serviceFqdn;
  private int port = 5679;
  private Duration discoveryInterval = Duration.ofSeconds(30);
  private Duration dnsResolutionTimeout = Duration.ofSeconds(5);

  @Override
  public NodeDiscoveryProvider.Type getType() {
    return KubernetesDiscoveryProvider.TYPE;
  }

  /**
   * Returns the Kubernetes service FQDN.
   *
   * @return the service FQDN
   */
  public String getServiceFqdn() {
    return serviceFqdn;
  }

  /**
   * Sets the Kubernetes service FQDN.
   *
   * @param serviceFqdn the service FQDN
   * @return the Kubernetes discovery configuration
   */
  public KubernetesDiscoveryConfig setServiceFqdn(final String serviceFqdn) {
    this.serviceFqdn = serviceFqdn;
    return this;
  }

  /**
   * Returns the port to use for discovered nodes.
   *
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Sets the port to use for discovered nodes.
   *
   * @param port the port
   * @return the Kubernetes discovery configuration
   */
  public KubernetesDiscoveryConfig setPort(final int port) {
    this.port = port;
    return this;
  }

  /**
   * Returns the discovery interval.
   *
   * @return the discovery interval
   */
  public Duration getDiscoveryInterval() {
    return discoveryInterval;
  }

  /**
   * Sets the discovery interval.
   *
   * @param discoveryInterval the discovery interval
   * @return the Kubernetes discovery configuration
   */
  public KubernetesDiscoveryConfig setDiscoveryInterval(final Duration discoveryInterval) {
    this.discoveryInterval = discoveryInterval;
    return this;
  }

  /**
   * Returns the DNS resolution timeout.
   *
   * @return the DNS resolution timeout
   */
  public Duration getDnsResolutionTimeout() {
    return dnsResolutionTimeout;
  }

  /**
   * Sets the DNS resolution timeout.
   *
   * @param dnsResolutionTimeout the DNS resolution timeout
   * @return the Kubernetes discovery configuration
   */
  public KubernetesDiscoveryConfig setDnsResolutionTimeout(final Duration dnsResolutionTimeout) {
    this.dnsResolutionTimeout = dnsResolutionTimeout;
    return this;
  }
}
