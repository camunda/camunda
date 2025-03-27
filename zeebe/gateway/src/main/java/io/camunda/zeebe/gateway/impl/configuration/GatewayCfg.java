/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import io.camunda.zeebe.util.ssl.SslConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GatewayCfg {

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();
  private SslConfig security = new SslConfig();
  private LongPollingCfg longPolling = new LongPollingCfg();
  private List<InterceptorCfg> interceptors = new ArrayList<>();
  private List<FilterCfg> filters = new ArrayList<>();

  public void init() {
    init(ConfigurationDefaults.DEFAULT_HOST);
  }

  public void init(final String defaultHost) {
    network.init(defaultHost);
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

  public SslConfig getSecurity() {
    return security;
  }

  public GatewayCfg setSecurity(final SslConfig security) {
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

  public List<InterceptorCfg> getInterceptors() {
    return interceptors;
  }

  public void setInterceptors(final List<InterceptorCfg> interceptors) {
    this.interceptors = interceptors;
  }

  public List<FilterCfg> getFilters() {
    return filters;
  }

  public void setFilters(final List<FilterCfg> filters) {
    this.filters = filters;
  }

  @Override
  public int hashCode() {
    return Objects.hash(network, cluster, threads, security, longPolling, interceptors);
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
        && Objects.equals(security, that.security)
        && Objects.equals(longPolling, that.longPolling)
        && Objects.equals(interceptors, that.interceptors);
  }

  @Override
  public String toString() {
    return "GatewayCfg{"
        + "network="
        + network
        + ", cluster="
        + cluster
        + ", threads="
        + threads
        + ", security="
        + security
        + ", longPolling="
        + longPolling
        + ", interceptors="
        + interceptors;
  }
}
