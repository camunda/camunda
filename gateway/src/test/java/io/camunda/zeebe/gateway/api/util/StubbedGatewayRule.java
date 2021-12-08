/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.util;

import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.camunda.zeebe.util.sched.testing.ActorRule;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;

public final class StubbedGatewayRule extends ExternalResource {

  protected StubbedGateway gateway;
  protected GatewayBlockingStub client;

  private final GatewayCfg config;
  private final ActorSchedulerRule actorSchedulerRule;
  private final ActorRule longPollingActorRule;
  private final StubbedBrokerClient brokerClient;
  private final Supplier<ActivateJobsHandler> activateJobsHandlerSupplier;

  public StubbedGatewayRule(
      final ActorSchedulerRule actorSchedulerRule,
      final ActorRule longPollingActorRule,
      final GatewayCfg config) {
    this.actorSchedulerRule = actorSchedulerRule;
    this.longPollingActorRule = longPollingActorRule;
    this.config = config;
    brokerClient = new StubbedBrokerClient();
    activateJobsHandlerSupplier = this::getActivateJobsHandler;
  }

  public ActivateJobsHandler getActivateJobsHandler() {
    if (config.getLongPolling().isEnabled()) {
      return LongPollingActivateJobsHandler.newBuilder()
          .setBrokerClient(brokerClient)
          .setActor(longPollingActorRule.getActorControl())
          .build();
    }
    return new RoundRobinActivateJobsHandler(brokerClient);
  }

  @Override
  protected void before() throws Throwable {
    final var activateJobsHandler = activateJobsHandlerSupplier.get();
    gateway = new StubbedGateway(actorSchedulerRule.get(), brokerClient, activateJobsHandler);
    gateway.start();
    client = gateway.buildClient();
  }

  @Override
  protected void after() {
    gateway.stop();
  }

  public StubbedGateway getGateway() {
    return gateway;
  }

  public GatewayBlockingStub getClient() {
    return client;
  }

  public StubbedBrokerClient getBrokerClient() {
    return brokerClient;
  }
}
