/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway;

import static java.lang.Runtime.getRuntime;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.core.Atomix;
import io.atomix.utils.net.Address;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import io.zeebe.gateway.impl.broker.BrokerClientFactory;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.configuration.ClusterCfg;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.util.TomlConfigurationReader;
import io.zeebe.util.sched.ActorScheduler;
import java.io.IOException;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;

public class StandaloneGateway {

  private final AtomixCluster atomixCluster;
  private final Gateway gateway;
  private final GatewayCfg gatewayCfg;
  private final ActorScheduler actorScheduler;

  public StandaloneGateway(final GatewayCfg gatewayCfg) {
    atomixCluster = createAtomixCluster(gatewayCfg.getCluster());
    actorScheduler = createActorScheduler(gatewayCfg);
    final BrokerClientFactory brokerClientFactory =
        (config, tracer) ->
            new BrokerClientImpl(config, atomixCluster, tracer, actorScheduler, false);
    gateway = new Gateway(gatewayCfg, actorScheduler, brokerClientFactory);
    this.gatewayCfg = gatewayCfg;
  }

  private AtomixCluster createAtomixCluster(final ClusterCfg clusterCfg) {
    final var atomix =
        Atomix.builder()
            .withMemberId(clusterCfg.getMemberId())
            .withAddress(Address.from(clusterCfg.getHost(), clusterCfg.getPort()))
            .withClusterId(clusterCfg.getClusterName())
            .withMembershipProvider(
                BootstrapDiscoveryProvider.builder()
                    .withNodes(Address.from(clusterCfg.getContactPoint()))
                    .build())
            .build();

    atomix.start();
    return atomix;
  }

  private ActorScheduler createActorScheduler(final GatewayCfg configuration) {
    final ActorScheduler actorScheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(configuration.getThreads().getManagementThreads())
            .setIoBoundActorThreadCount(0)
            .setSchedulerName("gateway-scheduler")
            .build();

    actorScheduler.start();

    return actorScheduler;
  }

  public void run() throws IOException, InterruptedException {
    HTTPServer monitoringServer = null;
    if (gatewayCfg.getMonitoring().isEnabled()) {
      monitoringServer =
          new HTTPServer(
              gatewayCfg.getMonitoring().getHost(), gatewayCfg.getMonitoring().getPort());
      DefaultExports.initialize();
    }

    gateway.listenAndServe();
    atomixCluster.stop();
    actorScheduler.stop();

    if (monitoringServer != null) {
      monitoringServer.stop();
    }
  }

  public static void main(final String[] args) throws Exception {
    final GatewayCfg gatewayCfg = initConfiguration(args);
    gatewayCfg.init();

    getRuntime()
        .addShutdownHook(
            new Thread("Gateway close thread") {
              @Override
              public void run() {
                LogManager.shutdown();
              }
            });

    new StandaloneGateway(gatewayCfg).run();
  }

  private static GatewayCfg initConfiguration(final String[] args) {
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
