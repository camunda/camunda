/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.clustering.mapper.S3LeaseConfig;
import io.camunda.zeebe.broker.exporter.debug.DebugLogExporter;
import io.camunda.zeebe.broker.exporter.metrics.MetricsExporter;
import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg;
import io.camunda.zeebe.util.Environment;
import java.util.HashMap;
import java.util.Map;

public class BrokerCfg {

  static final String ENV_DEBUG_EXPORTER = "ZEEBE_DEBUG";

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();
  private DataCfg data = new DataCfg();
  private Map<String, ExporterCfg> exporters = new HashMap<>();
  private ExportingCfg exporting = ExportingCfg.defaultExportingCfg();
  private EmbeddedGatewayCfg gateway = new EmbeddedGatewayCfg();
  private FlowControlCfg flowControl = new FlowControlCfg();
  private LimitCfg backpressure = new LimitCfg();
  private ProcessingCfg processingCfg = new ProcessingCfg();

  private ExperimentalCfg experimental = new ExperimentalCfg();
  private S3LeaseConfig leaseConfig = new S3LeaseConfig();

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
    flowControl.init(this, brokerBase);
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

  public ExportingCfg getExporting() {
    return exporting;
  }

  public void setExporting(final ExportingCfg exporting) {
    this.exporting = exporting;
  }

  public EmbeddedGatewayCfg getGateway() {
    return gateway;
  }

  public BrokerCfg setGateway(final EmbeddedGatewayCfg gateway) {
    this.gateway = gateway;
    return this;
  }

  public FlowControlCfg getFlowControl() {
    return flowControl;
  }

  public void setFlowControl(final FlowControlCfg flowControl) {
    this.flowControl = flowControl;
  }

  public LimitCfg getBackpressure() {
    return backpressure;
  }

  public BrokerCfg setBackpressure(final LimitCfg backpressure) {
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
        + ", exporting="
        + exporting
        + ", gateway="
        + gateway
        + ", flowControl="
        + flowControl
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

  public S3LeaseConfig getLeaseConfig() {
    return leaseConfig;
  }

  public void setLeaseConfig(final S3LeaseConfig leaseConfig) {
    this.leaseConfig = leaseConfig;
  }
}
