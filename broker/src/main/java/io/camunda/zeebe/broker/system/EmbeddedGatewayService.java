/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system;

import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClientImpl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import org.agrona.CloseHelper;

public final class EmbeddedGatewayService implements AutoCloseable {
  private final Gateway gateway;
  private final BrokerClientImpl brokerClient;
  private final JobStreamClient jobStreamClient;
  private final ConcurrencyControl concurrencyControl;

  public EmbeddedGatewayService(
      final BrokerCfg configuration,
      final ActorSchedulingService actorScheduler,
      final ClusterServices clusterServices,
      final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = concurrencyControl;
    brokerClient =
        new BrokerClientImpl(
            configuration.getGateway().getCluster().getRequestTimeout(),
            clusterServices.getMessagingService(),
            clusterServices.getMembershipService(),
            clusterServices.getEventService(),
            actorScheduler);
    jobStreamClient =
        new JobStreamClientImpl(actorScheduler, clusterServices.getCommunicationService());
    gateway =
        new Gateway(
            configuration.getGateway(), brokerClient, actorScheduler, jobStreamClient.streamer());
  }

  @Override
  public void close() {
    CloseHelper.closeAll(
        error ->
            Loggers.GATEWAY_LOGGER.warn(
                "Error occurred while shutting down embedded gateway", error),
        gateway,
        brokerClient,
        jobStreamClient);
  }

  public Gateway get() {
    return gateway;
  }

  public ActorFuture<Gateway> start() {
    jobStreamClient.start();

    // before we can add the job stream client as a topology listener, we need to wait for the
    // topology to be set up, otherwise the callback may be lost
    final var brokerClientStart =
        brokerClient.start().stream().collect(new ActorFutureCollector<>(concurrencyControl));
    concurrencyControl.runOnCompletion(
        brokerClientStart,
        (ok, error) -> brokerClient.getTopologyManager().addTopologyListener(jobStreamClient));

    return gateway.start();
  }
}
