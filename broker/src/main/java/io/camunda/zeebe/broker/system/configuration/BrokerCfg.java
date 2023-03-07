/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static io.camunda.zeebe.util.ObjectWriterFactory.getDefaultJsonObjectWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.broker.exporter.debug.DebugLogExporter;
import io.camunda.zeebe.broker.exporter.metrics.MetricsExporter;
import io.camunda.zeebe.broker.system.configuration.backpressure.BackpressureCfg;
import io.camunda.zeebe.util.Environment;
import io.camunda.zeebe.util.exception.UncheckedExecutionException;
import java.util.HashMap;
import java.util.Map;

public final class BrokerCfg {

  static final String ENV_DEBUG_EXPORTER = "ZEEBE_DEBUG";

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();
  private DataCfg data = new DataCfg();
  private Map<String, ExporterCfg> exporters = new HashMap<>();
  private EmbeddedGatewayCfg gateway = new EmbeddedGatewayCfg();
  private BackpressureCfg backpressure = new BackpressureCfg();
  private ProcessingCfg processingCfg = new ProcessingCfg();

  private ExperimentalCfg experimental = new ExperimentalCfg();

  private boolean executionMetricsExporterEnabled;

  public void init(final String brokerBase) {
    init(brokerBase, new Environment());
  }

  public void init(final String brokerBase, final Environment environment) {
    applyEnvironment(environment);

    if (isExecutionMetricsExporterEnabled()) {
      exporters.put(MetricsExporter.defaultExporterId(), MetricsExporter.defaultConfig());
    }

    network.init(this, brokerBase);
    cluster.init(this, brokerBase);
    threads.init(this, brokerBase);
    data.init(this, brokerBase);
    exporters.values().forEach(e -> e.init(this, brokerBase));
    gateway.init(this, brokerBase);
    backpressure.init(this, brokerBase);
    processingCfg.init(this, brokerBase);
    experimental.init(this, brokerBase);
  }

  private void applyEnvironment(final Environment environment) {
    if (environment.getBool(ENV_DEBUG_EXPORTER).orElse(false)) {
      exporters.put(DebugLogExporter.defaultExporterId(), DebugLogExporter.defaultConfig());
    }
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
    data = logs;
  }

  public Map<String, ExporterCfg> getExporters() {
    return exporters;
  }

  public void setExporters(final Map<String, ExporterCfg> exporters) {
    this.exporters = exporters;
  }

  public EmbeddedGatewayCfg getGateway() {
    return gateway;
  }

  public BrokerCfg setGateway(final EmbeddedGatewayCfg gateway) {
    this.gateway = gateway;
    return this;
  }

  public BackpressureCfg getBackpressure() {
    return backpressure;
  }

  public BrokerCfg setBackpressure(final BackpressureCfg backpressure) {
    this.backpressure = backpressure;
    return this;
  }

  public boolean isExecutionMetricsExporterEnabled() {
    return executionMetricsExporterEnabled;
  }

  public void setExecutionMetricsExporterEnabled(final boolean executionMetricsExporterEnabled) {
    this.executionMetricsExporterEnabled = executionMetricsExporterEnabled;
  }

  public ProcessingCfg getProcessing() {
    return processingCfg;
  }

  public void setProcessingCfg(final ProcessingCfg cfg) {
    processingCfg = cfg;
  }

  public ExperimentalCfg getExperimental() {
    return experimental;
  }

  public void setExperimental(final ExperimentalCfg experimental) {
    this.experimental = experimental;
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
        + ", backpressure="
        + backpressure
        + ", processingCfg="
        + processingCfg
        + ", experimental="
        + experimental
        + ", executionMetricsExporterEnabled="
        + executionMetricsExporterEnabled
        + '}';
  }

  public String toJson() {
    try {
      return getDefaultJsonObjectWriter().writeValueAsString(this);
    } catch (final JsonProcessingException e) {
      throw new UncheckedExecutionException("Writing to JSON failed", e);
    }
  }
}
