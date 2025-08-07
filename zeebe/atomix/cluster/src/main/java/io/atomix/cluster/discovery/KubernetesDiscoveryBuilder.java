/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.discovery;

import java.time.Duration;

/** Kubernetes headless service discovery builder. */
public class KubernetesDiscoveryBuilder extends NodeDiscoveryBuilder {
  private final KubernetesDiscoveryConfig config = new KubernetesDiscoveryConfig();

  /**
   * Sets the Kubernetes service FQDN.
   *
   * @param serviceFqdn the service FQDN
   * @return the Kubernetes discovery builder
   */
  public KubernetesDiscoveryBuilder withServiceFqdn(final String serviceFqdn) {
    config.setServiceFqdn(serviceFqdn);
    return this;
  }

  /**
   * Sets the port to use for discovered nodes.
   *
   * @param port the port
   * @return the Kubernetes discovery builder
   */
  public KubernetesDiscoveryBuilder withPort(final int port) {
    config.setPort(port);
    return this;
  }

  /**
   * Sets the discovery interval.
   *
   * @param discoveryInterval the discovery interval
   * @return the Kubernetes discovery builder
   */
  public KubernetesDiscoveryBuilder withDiscoveryInterval(final Duration discoveryInterval) {
    config.setDiscoveryInterval(discoveryInterval);
    return this;
  }

  /**
   * Sets the DNS resolution timeout.
   *
   * @param dnsResolutionTimeout the DNS resolution timeout
   * @return the Kubernetes discovery builder
   */
  public KubernetesDiscoveryBuilder withDnsResolutionTimeout(final Duration dnsResolutionTimeout) {
    config.setDnsResolutionTimeout(dnsResolutionTimeout);
    return this;
  }

  @Override
  public NodeDiscoveryProvider build() {
    return new KubernetesDiscoveryProvider(config);
  }
}
