/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.util.ObjectWriterFactory.getDefaultJsonObjectWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.zeebe.util.exception.UncheckedExecutionException;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zeebe.gateway")
public class GatewayCfg {

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();
  private MonitoringCfg monitoring = new MonitoringCfg();
  private SecurityCfg security = new SecurityCfg();
  private LongPollingCfg longPolling = new LongPollingCfg();
  private boolean initialized = false;

  public void init() {
    init(ConfigurationDefaults.DEFAULT_HOST);
  }

  public void init(final String defaultHost) {
    network.init(defaultHost);
    monitoring.init(defaultHost);
    initialized = true;
  }

  public boolean isInitialized() {
    return initialized;
  }

  public NetworkCfg getNetwork() {
    return network;
  }

  public GatewayCfg setNetwork(final NetworkCfg network) {
    this.network = network;
    return this;
  }

  public ClusterCfg getCluster() {
    return cluster;
  }

  public GatewayCfg setCluster(final ClusterCfg cluster) {
    this.cluster = cluster;
    return this;
  }

  public ThreadsCfg getThreads() {
    return threads;
  }

  public GatewayCfg setThreads(final ThreadsCfg threads) {
    this.threads = threads;
    return this;
  }

  public MonitoringCfg getMonitoring() {
    return monitoring;
  }

  public GatewayCfg setMonitoring(final MonitoringCfg monitoring) {
    this.monitoring = monitoring;
    return this;
  }

  public SecurityCfg getSecurity() {
    return security;
  }

  public GatewayCfg setSecurity(final SecurityCfg security) {
    this.security = security;
    return this;
  }

  public LongPollingCfg getLongPolling() {
    return longPolling;
  }

  public GatewayCfg setLongPolling(final LongPollingCfg longPolling) {
    this.longPolling = longPolling;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(network, cluster, threads, monitoring, security, longPolling);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GatewayCfg that = (GatewayCfg) o;
    return Objects.equals(network, that.network)
        && Objects.equals(cluster, that.cluster)
        && Objects.equals(threads, that.threads)
        && Objects.equals(monitoring, that.monitoring)
        && Objects.equals(security, that.security)
        && Objects.equals(longPolling, that.longPolling);
  }

  @Override
  public String toString() {
    return "GatewayCfg{"
        + "networkCfg="
        + network
        + ", clusterCfg="
        + cluster
        + ", threadsCfg="
        + threads
        + ", monitoringCfg="
        + monitoring
        + ", securityCfg="
        + security
        + ", longPollingCfg="
        + longPolling
        + '}';
  }

  public String toJson() {
    try {
      return getDefaultJsonObjectWriter().writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new UncheckedExecutionException("Writing to JSON failed", e);
    }
  }
}
