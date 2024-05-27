/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.shared.BrokerConfiguration;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled.RestGatewayDisabled;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.JobActivationRequestResponseObserver;
import io.camunda.zeebe.gateway.rest.controller.ResponseObserverProvider;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Entry point for the broker modules by using the the {@link io.camunda.application.Profile#BROKER}
 * profile, so that the appropriate broker application properties are applied.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = {
      "io.camunda.zeebe.broker",
      "io.camunda.zeebe.shared",
      "io.camunda.zeebe.gateway.rest",
      "io.camunda.authentication"
    })
@ConfigurationPropertiesScan(
    basePackages = {
      "io.camunda.zeebe.broker",
      "io.camunda.zeebe.shared",
      "io.camunda.authentication"
    })
@EnableAutoConfiguration
@Profile("broker")
public class BrokerModuleConfiguration implements CloseableSilently {
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  private final BrokerConfiguration configuration;
  private final IdentityConfiguration identityConfiguration;
  private final SpringBrokerBridge springBrokerBridge;
  private final ActorScheduler actorScheduler;
  private final AtomixCluster cluster;
  private final BrokerClient brokerClient;
  private final BrokerShutdownHelper shutdownHelper;

  private Broker broker;

  @Autowired
  public BrokerModuleConfiguration(
      final BrokerConfiguration configuration,
      final IdentityConfiguration identityConfiguration,
      final SpringBrokerBridge springBrokerBridge,
      final ActorScheduler actorScheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final BrokerShutdownHelper shutdownHelper) {
    this.configuration = configuration;
    this.identityConfiguration = identityConfiguration;
    this.springBrokerBridge = springBrokerBridge;
    this.actorScheduler = actorScheduler;
    this.cluster = cluster;
    this.brokerClient = brokerClient;
    this.shutdownHelper = shutdownHelper;
  }

  @Bean(destroyMethod = "close")
  public Broker broker() {
    final SystemContext systemContext =
        new SystemContext(
            configuration.shutdownTimeout(),
            configuration.config(),
            identityConfiguration,
            actorScheduler,
            cluster,
            brokerClient);

    springBrokerBridge.registerShutdownHelper(
        errorCode -> shutdownHelper.initiateShutdown(errorCode));
    broker = new Broker(systemContext, springBrokerBridge);

    // already initiate starting the broker
    // to ensure that the necessary ports
    // get opened and other apps like
    // Operate can connect
    startBroker();

    return broker;
  }

  protected void startBroker() {
    broker.start();
  }

  protected void stopBroker() {
    try {
      broker.close();
    } finally {
      cleanupWorkingDirectory();
    }
  }

  private void cleanupWorkingDirectory() {
    final var workingDirectory = configuration.workingDirectory();
    if (!workingDirectory.isTemporary()) {
      return;
    }

    LOGGER.debug("Deleting broker temporary working directory {}", workingDirectory.path());
    try {
      FileUtil.deleteFolderIfExists(workingDirectory.path());
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete temporary directory {}", workingDirectory.path());
    }
  }

  @Bean
  @ConditionalOnMissingBean(value = RestGatewayDisabled.class)
  public ResponseObserverProvider responseObserverProvider() {
    return JobActivationRequestResponseObserver::new;
  }

  @Bean
  @ConditionalOnMissingBean(value = RestGatewayDisabled.class)
  public ActivateJobsHandler<JobActivationResponse> activateJobsHandler() {
    final var handler = buildActivateJobsHandler(brokerClient);
    final var future = new CompletableFuture<ActivateJobsHandler<JobActivationResponse>>();
    final var actor =
        Actor.newActor()
            .name("ActivateJobsHandlerRest-Broker")
            .actorStartedHandler(handler.andThen(t -> future.complete(handler)))
            .build();
    actorScheduler.submitActor(actor);
    return handler;
  }

  private ActivateJobsHandler<JobActivationResponse> buildActivateJobsHandler(
      final BrokerClient brokerClient) {
    if (configuration.config().getGateway().getLongPolling().isEnabled()) {
      return buildLongPollingHandler(brokerClient);
    } else {
      return new RoundRobinActivateJobsHandler<>(
          brokerClient,
          configuration.config().getGateway().getNetwork().getMaxMessageSize().toBytes(),
          ResponseMapper::toActivateJobsResponse,
          RuntimeException::new);
    }
  }

  private LongPollingActivateJobsHandler<JobActivationResponse> buildLongPollingHandler(
      final BrokerClient brokerClient) {
    return LongPollingActivateJobsHandler.<JobActivationResponse>newBuilder()
        .setBrokerClient(brokerClient)
        .setMaxMessageSize(
            configuration.config().getGateway().getNetwork().getMaxMessageSize().toBytes())
        .setLongPollingTimeout(configuration.config().getGateway().getLongPolling().getTimeout())
        .setProbeTimeoutMillis(
            configuration.config().getGateway().getLongPolling().getProbeTimeout())
        .setMinEmptyResponses(
            configuration.config().getGateway().getLongPolling().getMinEmptyResponses())
        .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
        .setNoJobsReceivedExceptionProvider(RuntimeException::new)
        .setRequestCanceledExceptionProvider(RuntimeException::new)
        .build();
  }

  @Override
  public void close() {
    stopBroker();
  }
}
