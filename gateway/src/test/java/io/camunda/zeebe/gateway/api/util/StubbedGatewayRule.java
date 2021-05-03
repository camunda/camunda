/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.api.util;

import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.rules.ExternalResource;

public final class StubbedGatewayRule extends ExternalResource {

  protected StubbedGateway gateway;
  protected GatewayBlockingStub client;
  private final ActorSchedulerRule actorSchedulerRule;
  private final StubbedBrokerClient brokerClient;
  private final ActivateJobsHandler activateJobsHandler;

  public StubbedGatewayRule(final ActorSchedulerRule actorSchedulerRule, final GatewayCfg config) {
    this.actorSchedulerRule = actorSchedulerRule;
    brokerClient = new StubbedBrokerClient();
    activateJobsHandler = getActivateJobsHandler(config, brokerClient);
  }

  private static ActivateJobsHandler getActivateJobsHandler(
      final GatewayCfg config, final StubbedBrokerClient brokerClient) {
    if (config.getLongPolling().isEnabled()) {
      return LongPollingActivateJobsHandler.newBuilder().setBrokerClient(brokerClient).build();
    }
    return new RoundRobinActivateJobsHandler(brokerClient);
  }

  @Override
  protected void before() throws Throwable {
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
