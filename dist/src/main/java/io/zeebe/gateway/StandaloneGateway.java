/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.utils.net.Address;
import io.prometheus.client.exporter.HTTPServer;
import io.zeebe.gateway.impl.configuration.ClusterCfg;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.util.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Paths;

public class StandaloneGateway {

  private final AtomixCluster atomixCluster;
  private final Gateway gateway;
  private final GatewayCfg gatewayCfg;

  public StandaloneGateway(GatewayCfg gatewayCfg) {
    atomixCluster = createAtomixCluster(gatewayCfg.getCluster());
    gateway = new Gateway(gatewayCfg, atomixCluster);
    this.gatewayCfg = gatewayCfg;
  }

  private AtomixCluster createAtomixCluster(ClusterCfg clusterCfg) {
    final AtomixCluster atomixCluster =
        AtomixCluster.builder()
            .withMemberId(clusterCfg.getMemberId())
            .withAddress(Address.from(clusterCfg.getHost(), clusterCfg.getPort()))
            .withClusterId(clusterCfg.getClusterName())
            .withMembershipProvider(
                BootstrapDiscoveryProvider.builder()
                    .withNodes(Address.from(clusterCfg.getContactPoint()))
                    .build())
            .build();

    atomixCluster.start();

    return atomixCluster;
  }

  public void run() throws IOException, InterruptedException {
    HTTPServer monitoringServer = null;
    if (gatewayCfg.getMonitoring().isEnabled()) {
      monitoringServer =
          new HTTPServer(
              gatewayCfg.getMonitoring().getHost(), gatewayCfg.getMonitoring().getPort());
    }

    gateway.listenAndServe();
    atomixCluster.stop();

    if (monitoringServer != null) {
      monitoringServer.stop();
    }
  }

  public static void main(String args[]) throws Exception {
    final GatewayCfg gatewayCfg = initConfiguration(args);
    gatewayCfg.init();
    new StandaloneGateway(gatewayCfg).run();
  }

  private static GatewayCfg initConfiguration(String[] args) {
    if (args.length >= 1) {
      String configFileLocation = args[0];

      if (!Paths.get(configFileLocation).isAbsolute()) {
        configFileLocation =
            Paths.get(getBasePath(), configFileLocation).toAbsolutePath().normalize().toString();
      }

      return TomlConfigurationReader.read(configFileLocation, GatewayCfg.class);
    } else {
      return new GatewayCfg();
    }
  }

  private static String getBasePath() {
    String basePath = System.getProperty("basedir");

    if (basePath == null) {
      basePath = Paths.get(".").toAbsolutePath().normalize().toString();
    }

    return basePath;
  }
}
