/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import com.google.gson.GsonBuilder;
import io.zeebe.broker.exporter.debug.DebugLogExporter;
import io.zeebe.util.Environment;
import java.util.ArrayList;
import java.util.List;

public class BrokerCfg {

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();
  private DataCfg data = new DataCfg();
  private List<ExporterCfg> exporters = new ArrayList<>();
  private EmbeddedGatewayCfg gateway = new EmbeddedGatewayCfg();

  public void init(final String brokerBase) {
    init(brokerBase, new Environment());
  }

  public void init(final String brokerBase, final Environment environment) {
    applyEnvironment(environment);
    network.init(this, brokerBase, environment);
    cluster.init(this, brokerBase, environment);
    threads.init(this, brokerBase, environment);
    data.init(this, brokerBase, environment);
    exporters.forEach(e -> e.init(this, brokerBase, environment));
    gateway.init(this, brokerBase, environment);
  }

  private void applyEnvironment(final Environment environment) {
    environment
        .get(EnvironmentConstants.ENV_DEBUG_EXPORTER)
        .ifPresent(
            value ->
                exporters.add(DebugLogExporter.defaultConfig("pretty".equalsIgnoreCase(value))));
  }

  public NetworkCfg getNetwork() {
    return network;
  }

  public void setNetwork(final NetworkCfg network) {
    this.network = network;
  }

  public ClusterCfg getCluster() {
    return cluster;
  }

  public void setCluster(final ClusterCfg cluster) {
    this.cluster = cluster;
  }

  public ThreadsCfg getThreads() {
    return threads;
  }

  public void setThreads(final ThreadsCfg threads) {
    this.threads = threads;
  }

  public DataCfg getData() {
    return data;
  }

  public void setData(final DataCfg logs) {
    this.data = logs;
  }

  public List<ExporterCfg> getExporters() {
    return exporters;
  }

  public void setExporters(final List<ExporterCfg> exporters) {
    this.exporters = exporters;
  }

  public EmbeddedGatewayCfg getGateway() {
    return gateway;
  }

  public BrokerCfg setGateway(EmbeddedGatewayCfg gateway) {
    this.gateway = gateway;
    return this;
  }

  @Override
  public String toString() {
    return "BrokerCfg{"
        + "network="
        + network
        + ", cluster="
        + cluster
        + ", threads="
        + threads
        + ", data="
        + data
        + ", exporters="
        + exporters
        + ", gateway="
        + gateway
        + '}';
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
