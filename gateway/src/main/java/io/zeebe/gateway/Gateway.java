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
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.TracingServerInterceptor;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerClientFactory;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.gateway.impl.configuration.NetworkCfg;
import io.zeebe.gateway.impl.configuration.SecurityCfg;
import io.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.zeebe.util.VersionUtil;
import io.zeebe.util.sched.ActorScheduler;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import me.dinowernli.grpc.prometheus.Configuration;
import me.dinowernli.grpc.prometheus.MonitoringServerInterceptor;
import org.slf4j.Logger;

public final class Gateway {
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;
  private static final Function<GatewayCfg, ServerBuilder<?>> DEFAULT_SERVER_BUILDER_FACTORY =
      cfg -> setNetworkConfig(cfg.getNetwork());

  private final Function<GatewayCfg, ServerBuilder<?>> serverBuilderFactory;
  private final BrokerClientFactory clientFactory;
  private final GatewayCfg gatewayCfg;
  private final ActorScheduler actorScheduler;

  private Server server;
  private BrokerClient brokerClient;
  private Tracer tracer;

  public Gateway(
      final GatewayCfg gatewayCfg,
      final AtomixCluster atomixCluster,
      final ActorScheduler actorScheduler) {
    this(
        gatewayCfg,
        actorScheduler,
        (cfg, tracer) -> new BrokerClientImpl(cfg, atomixCluster, tracer),
        DEFAULT_SERVER_BUILDER_FACTORY);
  }

  public Gateway(
      final GatewayCfg gatewayCfg,
      final ActorScheduler actorScheduler,
      final BrokerClientFactory clientFactory) {
    this(gatewayCfg, actorScheduler, clientFactory, DEFAULT_SERVER_BUILDER_FACTORY);
  }

  public Gateway(
      final GatewayCfg gatewayCfg,
      final ActorScheduler actorScheduler,
      final BrokerClientFactory clientFactory,
      final Function<GatewayCfg, ServerBuilder<?>> serverBuilderFactory) {
    this.gatewayCfg = gatewayCfg;
    this.clientFactory = clientFactory;
    this.serverBuilderFactory = serverBuilderFactory;
    this.actorScheduler = actorScheduler;
  }

  public GatewayCfg getGatewayCfg() {
    return gatewayCfg;
  }

  public BrokerClient getBrokerClient() {
    return brokerClient;
  }

  public void start() throws IOException {
    if (LOG.isInfoEnabled()) {
      LOG.info("Version: {}", VersionUtil.getVersion());
      LOG.info("Starting gateway with configuration {}", gatewayCfg.toJson());
    }

    final var interceptors = new ArrayList<ServerInterceptor>();
    tracer = NoopTracerFactory.create(); // NOSONAR
    if (gatewayCfg.getMonitoring().isEnabled()) {
      interceptors.add(MonitoringServerInterceptor.create(Configuration.allMetrics()));

      if (gatewayCfg.getMonitoring().isTracing()) {
        tracer = TracerResolver.resolveTracer(getClass().getClassLoader());
        interceptors.add(
            TracingServerInterceptor.newBuilder().withTracer(tracer).withStreaming().build());
      }
    }

    brokerClient = buildBrokerClient(tracer);

    final LongPollingActivateJobsHandler longPollingHandler = buildLongPollingHandler(brokerClient);
    actorScheduler.submitActor(longPollingHandler);

    final EndpointManager endpointManager = new EndpointManager(brokerClient, longPollingHandler);
    final ServerBuilder<?> serverBuilder = serverBuilderFactory.apply(gatewayCfg);
    serverBuilder.addService(ServerInterceptors.intercept(endpointManager, interceptors));

    final SecurityCfg securityCfg = gatewayCfg.getSecurity();
    if (securityCfg.isEnabled()) {
      setSecurityConfig(serverBuilder, securityCfg);
    }

    server = serverBuilder.build();

    server.start();
  }

  private static NettyServerBuilder setNetworkConfig(final NetworkCfg cfg) {
    final Duration minKeepAliveInterval = cfg.getMinKeepAliveIntervalAsDuration();

    if (minKeepAliveInterval.isNegative() || minKeepAliveInterval.isZero()) {
      throw new IllegalArgumentException("Minimum keep alive interval must be positive.");
    }

    return NettyServerBuilder.forAddress(new InetSocketAddress(cfg.getHost(), cfg.getPort()))
        .permitKeepAliveTime(minKeepAliveInterval.toMillis(), TimeUnit.MILLISECONDS)
        .permitKeepAliveWithoutCalls(false);
  }

  private void setSecurityConfig(final ServerBuilder<?> serverBuilder, final SecurityCfg security) {
    if (security.getCertificateChainPath() == null) {
      throw new IllegalArgumentException(
          "Expected to find a valid path to a certificate chain but none was found. "
              + "Edit the gateway configuration file to provide one or to disable TLS.");
    }

    if (security.getPrivateKeyPath() == null) {
      throw new IllegalArgumentException(
          "Expected to find a valid path to a private key but none was found. "
              + "Edit the gateway configuration file to provide one or to disable TLS.");
    }

    final File certChain = new File(security.getCertificateChainPath());
    final File privateKey = new File(security.getPrivateKeyPath());

    if (!certChain.exists()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected to find a certificate chain file at the provided location '%s' but none was found.",
              security.getCertificateChainPath()));
    }

    if (!privateKey.exists()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected to find a private key file at the provided location '%s' but none was found.",
              security.getPrivateKeyPath()));
    }

    serverBuilder.useTransportSecurity(certChain, privateKey);
  }

  private BrokerClient buildBrokerClient(final Tracer tracer) {
    return clientFactory.newClient(gatewayCfg, tracer);
  }

  private LongPollingActivateJobsHandler buildLongPollingHandler(final BrokerClient brokerClient) {
    return LongPollingActivateJobsHandler.newBuilder().setBrokerClient(brokerClient).build();
  }

  public void listenAndServe() throws InterruptedException, IOException {
    start();
    server.awaitTermination();
  }

  public void stop() {
    if (server != null && !server.isShutdown()) {
      server.shutdownNow();
      try {
        server.awaitTermination();
      } catch (final InterruptedException e) {
        LOG.error("Failed to await termination of gateway", e);
        Thread.currentThread().interrupt();
      } finally {
        server = null;
      }
    }

    if (brokerClient != null) {
      brokerClient.close();
      brokerClient = null;
    }

    if (tracer != null) {
      tracer.close();
      tracer = null;
    }
  }
}
