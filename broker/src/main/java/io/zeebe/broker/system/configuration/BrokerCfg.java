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
import io.zeebe.util.DurationUtil;
import io.zeebe.util.Environment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zeebe-broker")
public final class BrokerCfg {

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();
  private DataCfg data = new DataCfg();
  private List<ExporterCfg> exporters = new ArrayList<>();
  private EmbeddedGatewayCfg gateway = new EmbeddedGatewayCfg();
  private BackpressureCfg backpressure = new BackpressureCfg();

  private String stepTimeout = "5m";

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
    backpressure.init(this, brokerBase, environment);
  }

  private void applyEnvironment(final Environment environment) {
    environment
        .get(EnvironmentConstants.ENV_DEBUG_EXPORTER)
        .ifPresent(
            value ->
                exporters.add(DebugLogExporter.defaultConfig("pretty".equalsIgnoreCase(value))));
    environment.get(EnvironmentConstants.ENV_STEP_TIMEOUT).ifPresent(this::setStepTimeout);
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

  public Duration getStepTimeoutAsDuration() {
    return DurationUtil.parse(stepTimeout);
  }

  public String getStepTimeout() {
    return stepTimeout;
  }

  public void setStepTimeout(final String stepTimeout) {
    // call parsing code to provoke any exceptions that might occur during parsing
    DurationUtil.parse(stepTimeout);

    this.stepTimeout = stepTimeout;
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
        + ", stepTimeout='"
        + stepTimeout
        + '\''
        + '}';
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
