/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.test;

import io.zeebe.broker.exporter.debug.DebugHttpExporter;
import io.zeebe.broker.exporter.debug.DebugLogExporter;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Arrays;
import java.util.function.Consumer;

public class EmbeddedBrokerConfigurator {

  public static final Consumer<BrokerCfg> DEBUG_EXPORTER =
      cfg -> cfg.getExporters().add(DebugLogExporter.defaultConfig(false));

  public static final Consumer<BrokerCfg> HTTP_EXPORTER =
      cfg -> cfg.getExporters().add(DebugHttpExporter.defaultConfig());

  public static final Consumer<BrokerCfg> TEST_RECORDER =
      cfg -> {
        final ExporterCfg exporterCfg = new ExporterCfg();
        exporterCfg.setId("test-recorder");
        exporterCfg.setClassName(RecordingExporter.class.getName());
        cfg.getExporters().add(exporterCfg);
      };

  public static final Consumer<BrokerCfg> DISABLE_EMBEDDED_GATEWAY =
      cfg -> cfg.getGateway().setEnable(false);

  public static Consumer<BrokerCfg> setPartitionCount(final int partitionCount) {
    return cfg -> cfg.getCluster().setPartitionsCount(partitionCount);
  }

  public static Consumer<BrokerCfg> setCluster(
      final int nodeId,
      final int partitionCount,
      final int replicationFactor,
      final int clusterSize,
      final String clusterName) {
    return cfg -> {
      final ClusterCfg cluster = cfg.getCluster();
      cluster.setNodeId(nodeId);
      cluster.setPartitionsCount(partitionCount);
      cluster.setReplicationFactor(replicationFactor);
      cluster.setClusterSize(clusterSize);
      cluster.setClusterName(clusterName);
    };
  }

  public static Consumer<BrokerCfg> setInitialContactPoints(final String... contactPoints) {
    return cfg -> cfg.getCluster().setInitialContactPoints(Arrays.asList(contactPoints));
  }

  public static Consumer<BrokerCfg> setGatewayApiPort(final int port) {
    return cfg -> cfg.getGateway().getNetwork().setPort(port);
  }

  public static Consumer<BrokerCfg> setGatewayClusterPort(final int port) {
    return cfg -> cfg.getGateway().getCluster().setPort(port);
  }

  public static Consumer<BrokerCfg> setCommandApiPort(final int port) {
    return cfg -> cfg.getNetwork().getCommandApi().setPort(port);
  }

  public static Consumer<BrokerCfg> setInternalApiPort(final int port) {
    return cfg -> cfg.getNetwork().getInternalApi().setPort(port);
  }

  public static Consumer<BrokerCfg> setMonitoringPort(final int port) {
    return cfg -> cfg.getNetwork().getMonitoringApi().setPort(port);
  }
}
