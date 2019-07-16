/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Function;
import me.dinowernli.grpc.prometheus.Configuration;
import me.dinowernli.grpc.prometheus.MonitoringServerInterceptor;
import org.slf4j.Logger;

public class Gateway {

  public static final String VERSION;
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;
  private static final Function<GatewayCfg, ServerBuilder> DEFAULT_SERVER_BUILDER_FACTORY =
      cfg ->
          NettyServerBuilder.forAddress(
              new InetSocketAddress(cfg.getNetwork().getHost(), cfg.getNetwork().getPort()));

  static {
    final String version = Gateway.class.getPackage().getImplementationVersion();
    VERSION = version != null ? version : "development";
  }

  private final Function<GatewayCfg, ServerBuilder> serverBuilderFactory;
  private final Function<GatewayCfg, BrokerClient> brokerClientFactory;
  private final GatewayCfg gatewayCfg;

  private Server server;
  private BrokerClient brokerClient;

  public Gateway(GatewayCfg gatewayCfg, AtomixCluster atomixCluster) {
    this(
        gatewayCfg,
        cfg -> new BrokerClientImpl(cfg, atomixCluster),
        DEFAULT_SERVER_BUILDER_FACTORY);
  }

  public Gateway(GatewayCfg gatewayCfg, Function<GatewayCfg, BrokerClient> brokerClientFactory) {
    this(gatewayCfg, brokerClientFactory, DEFAULT_SERVER_BUILDER_FACTORY);
  }

  public Gateway(
      GatewayCfg gatewayCfg,
      Function<GatewayCfg, BrokerClient> brokerClientFactory,
      Function<GatewayCfg, ServerBuilder> serverBuilderFactory) {
    this.gatewayCfg = gatewayCfg;
    this.brokerClientFactory = brokerClientFactory;
    this.serverBuilderFactory = serverBuilderFactory;
  }

  public GatewayCfg getGatewayCfg() {
    return gatewayCfg;
  }

  public BrokerClient getBrokerClient() {
    return brokerClient;
  }

  public void start() throws IOException {
    LOG.info("Version: {}", VERSION);
    LOG.info("Starting gateway with configuration {}", gatewayCfg.toJson());

    brokerClient = buildBrokerClient();

    final EndpointManager endpointManager = new EndpointManager(brokerClient);

    final ServerBuilder serverBuilder = serverBuilderFactory.apply(gatewayCfg);

    if (gatewayCfg.getMonitoring().isEnabled()) {
      final MonitoringServerInterceptor monitoringInterceptor =
          MonitoringServerInterceptor.create(Configuration.allMetrics());
      serverBuilder.addService(
          ServerInterceptors.intercept(endpointManager, monitoringInterceptor));
    } else {
      serverBuilder.addService(endpointManager);
    }

    server = serverBuilder.build();

    server.start();
  }

  protected BrokerClient buildBrokerClient() {
    return brokerClientFactory.apply(gatewayCfg);
  }

  public void listenAndServe() throws InterruptedException, IOException {
    start();
    server.awaitTermination();
  }

  public void stop() {
    if (server != null && !server.isShutdown()) {
      server.shutdown();
      try {
        server.awaitTermination();
      } catch (InterruptedException e) {
        LOG.error("Failed to await termination of gateway", e);
      } finally {
        server = null;
      }
    }

    if (brokerClient != null) {
      brokerClient.close();
      brokerClient = null;
    }
  }
}
