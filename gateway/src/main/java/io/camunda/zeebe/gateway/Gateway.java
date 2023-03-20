/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.camunda.zeebe.gateway.health.GatewayHealthManager;
import io.camunda.zeebe.gateway.health.Status;
import io.camunda.zeebe.gateway.health.impl.GatewayHealthManagerImpl;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.configuration.AuthenticationCfg.AuthMode;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler;
import io.camunda.zeebe.gateway.interceptors.impl.ContextInjectingInterceptor;
import io.camunda.zeebe.gateway.interceptors.impl.DecoratedInterceptor;
import io.camunda.zeebe.gateway.interceptors.impl.IdentityInterceptor;
import io.camunda.zeebe.gateway.interceptors.impl.InterceptorRepository;
import io.camunda.zeebe.gateway.query.impl.QueryApiImpl;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.NettyServerBuilder;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.dinowernli.grpc.prometheus.Configuration;
import me.dinowernli.grpc.prometheus.MonitoringServerInterceptor;
import org.slf4j.Logger;

public final class Gateway {
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;
  private static final MonitoringServerInterceptor MONITORING_SERVER_INTERCEPTOR =
      MonitoringServerInterceptor.create(Configuration.allMetrics());

  private final Function<GatewayCfg, ServerBuilder> serverBuilderFactory =
      cfg -> setNetworkConfig(cfg.getNetwork());
  private final GatewayCfg gatewayCfg;
  private final ActorSchedulingService actorSchedulingService;
  private final GatewayHealthManager healthManager;

  private Server server;
  private final BrokerClient brokerClient;

  public Gateway(
      final GatewayCfg gatewayCfg,
      final BrokerClient brokerClient,
      final ActorSchedulingService actorSchedulingService) {
    this.gatewayCfg = gatewayCfg;
    this.brokerClient = brokerClient;
    this.actorSchedulingService = actorSchedulingService;

    healthManager = new GatewayHealthManagerImpl();
  }

  public GatewayCfg getGatewayCfg() {
    return gatewayCfg;
  }

  public Status getStatus() {
    return healthManager.getStatus();
  }

  public BrokerClient getBrokerClient() {
    return brokerClient;
  }

  public ActorFuture<Gateway> start() {
    final var resultFuture = new CompletableActorFuture<Gateway>();

    healthManager.setStatus(Status.STARTING);

    createAndStartActivateJobsHandler(brokerClient)
        .whenComplete(
            (activateJobsHandler, error) -> {
              if (error != null) {
                resultFuture.completeExceptionally(error);
                return;
              }

              final var serverResult = createAndStartServer(activateJobsHandler);
              if (serverResult.isLeft()) {
                final var exception = serverResult.getLeft();
                resultFuture.completeExceptionally(exception);
              } else {
                server = serverResult.get();
                healthManager.setStatus(Status.RUNNING);
                resultFuture.complete(this);
              }
            });

    return resultFuture;
  }

  private Either<Exception, Server> createAndStartServer(
      final ActivateJobsHandler activateJobsHandler) {
    final EndpointManager endpointManager = new EndpointManager(brokerClient, activateJobsHandler);
    final GatewayGrpcService gatewayGrpcService = new GatewayGrpcService(endpointManager);

    try {
      final var serverBuilder = serverBuilderFactory.apply(gatewayCfg);
      applySecurityConfigurationIfEnabled(serverBuilder);
      final var server = buildServer(serverBuilder, gatewayGrpcService);
      server.start();
      return Either.right(server);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  private void applySecurityConfigurationIfEnabled(final ServerBuilder<?> serverBuilder) {
    final SecurityCfg securityCfg = gatewayCfg.getSecurity();
    if (securityCfg.isEnabled()) {
      setSecurityConfig(serverBuilder, securityCfg);
    }
  }

  private Server buildServer(
      final ServerBuilder<?> serverBuilder, final BindableService interceptorService) {
    return serverBuilder
        .addService(applyInterceptors(interceptorService))
        .addService(
            ServerInterceptors.intercept(
                healthManager.getHealthService(), MONITORING_SERVER_INTERCEPTOR))
        .build();
  }

  private static NettyServerBuilder setNetworkConfig(final NetworkCfg cfg) {
    final Duration minKeepAliveInterval = cfg.getMinKeepAliveInterval();

    if (minKeepAliveInterval.isNegative() || minKeepAliveInterval.isZero()) {
      throw new IllegalArgumentException("Minimum keep alive interval must be positive.");
    }

    final var maxMessageSize = (int) cfg.getMaxMessageSize().toBytes();
    if (maxMessageSize <= 0) {
      throw new IllegalArgumentException("maxMessageSize must be positive");
    }

    return NettyServerBuilder.forAddress(new InetSocketAddress(cfg.getHost(), cfg.getPort()))
        .maxInboundMessageSize(maxMessageSize)
        .permitKeepAliveTime(minKeepAliveInterval.toMillis(), TimeUnit.MILLISECONDS)
        .permitKeepAliveWithoutCalls(false);
  }

  private void setSecurityConfig(final ServerBuilder<?> serverBuilder, final SecurityCfg security) {
    final var certificateChainPath = security.getCertificateChainPath();
    final var privateKeyPath = security.getPrivateKeyPath();

    if (certificateChainPath == null) {
      throw new IllegalArgumentException(
          "Expected to find a valid path to a certificate chain but none was found. "
              + "Edit the gateway configuration file to provide one or to disable TLS.");
    }

    if (privateKeyPath == null) {
      throw new IllegalArgumentException(
          "Expected to find a valid path to a private key but none was found. "
              + "Edit the gateway configuration file to provide one or to disable TLS.");
    }

    if (!certificateChainPath.exists()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected to find a certificate chain file at the provided location '%s' but none was found.",
              certificateChainPath));
    }

    if (!privateKeyPath.exists()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected to find a private key file at the provided location '%s' but none was found.",
              privateKeyPath));
    }

    serverBuilder.useTransportSecurity(certificateChainPath, privateKeyPath);
  }

  private CompletableFuture<ActivateJobsHandler> createAndStartActivateJobsHandler(
      final BrokerClient brokerClient) {
    final var handler = buildActivateJobsHandler(brokerClient);
    return submitActorToActivateJobs(handler);
  }

  private CompletableFuture<ActivateJobsHandler> submitActorToActivateJobs(
      final ActivateJobsHandler handler) {
    final var future = new CompletableFuture<ActivateJobsHandler>();
    final var actor =
        Actor.newActor()
            .name("ActivateJobsHandler")
            .actorStartedHandler(handler.andThen(t -> future.complete(handler)))
            .build();
    actorSchedulingService.submitActor(actor);
    return future;
  }

  private ActivateJobsHandler buildActivateJobsHandler(final BrokerClient brokerClient) {
    if (gatewayCfg.getLongPolling().isEnabled()) {
      return buildLongPollingHandler(brokerClient);
    } else {
      return new RoundRobinActivateJobsHandler(brokerClient);
    }
  }

  private LongPollingActivateJobsHandler buildLongPollingHandler(final BrokerClient brokerClient) {
    return LongPollingActivateJobsHandler.newBuilder().setBrokerClient(brokerClient).build();
  }

  private ServerServiceDefinition applyInterceptors(final BindableService service) {
    final var repository = new InterceptorRepository().load(gatewayCfg.getInterceptors());
    final var queryApi = new QueryApiImpl(brokerClient);
    final List<ServerInterceptor> interceptors =
        repository.instantiate().map(DecoratedInterceptor::decorate).collect(Collectors.toList());

    // reverse the user interceptors, such that they will be called in the order in which they are
    // configured, such that the first configured interceptor is the outermost interceptor in the
    // chain
    Collections.reverse(interceptors);
    interceptors.add(new ContextInjectingInterceptor(queryApi));
    interceptors.add(MONITORING_SERVER_INTERCEPTOR);
    if (AuthMode.IDENTITY == gatewayCfg.getSecurity().getAuthentication().getMode()) {
      interceptors.add(
          new IdentityInterceptor(gatewayCfg.getSecurity().getAuthentication().getIdentity()));
    }

    return ServerInterceptors.intercept(service, interceptors);
  }

  public void stop() {
    healthManager.setStatus(Status.SHUTDOWN);

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
  }
}
