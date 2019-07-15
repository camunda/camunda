/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration;

import com.google.gson.GsonBuilder;
import io.zeebe.util.Environment;
import java.util.Objects;

public class GatewayCfg {

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();
  private MonitoringCfg monitoring = new MonitoringCfg();

  public void init() {
    init(new Environment());
  }

  public void init(Environment environment) {
    init(environment, ConfigurationDefaults.DEFAULT_HOST);
  }

  public void init(Environment environment, String defaultHost) {
    network.init(environment, defaultHost);
    cluster.init(environment);
    threads.init(environment);
    monitoring.init(environment, defaultHost);
  }

  public NetworkCfg getNetwork() {
    return network;
  }

  public GatewayCfg setNetwork(NetworkCfg network) {
    this.network = network;
    return this;
  }

  public ClusterCfg getCluster() {
    return cluster;
  }

  public GatewayCfg setCluster(ClusterCfg cluster) {
    this.cluster = cluster;
    return this;
  }

  public ThreadsCfg getThreads() {
    return threads;
  }

  public GatewayCfg setThreads(ThreadsCfg threads) {
    this.threads = threads;
    return this;
  }

  public MonitoringCfg getMonitoring() {
    return monitoring;
  }

  public GatewayCfg setMonitoring(MonitoringCfg monitoring) {
    this.monitoring = monitoring;
    return this;
  }

  @Override
  public boolean equals(Object o) {
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
        && Objects.equals(monitoring, that.monitoring);
  }

  @Override
  public int hashCode() {
    return Objects.hash(network, cluster, threads, monitoring);
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
        + '}';
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
