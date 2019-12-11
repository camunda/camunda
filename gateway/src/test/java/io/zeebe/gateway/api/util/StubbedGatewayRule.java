/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.util;

import io.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.rules.ExternalResource;

public class StubbedGatewayRule extends ExternalResource {

  protected StubbedGateway gateway;
  protected GatewayBlockingStub client;
  private final ActorSchedulerRule actorSchedulerRule;
  private final StubbedBrokerClient brokerClient;
  private final LongPollingActivateJobsHandler longPollingHandler;

  public StubbedGatewayRule(final ActorSchedulerRule actorSchedulerRule) {
    this.actorSchedulerRule = actorSchedulerRule;
    this.brokerClient = new StubbedBrokerClient();
    this.longPollingHandler =
        LongPollingActivateJobsHandler.newBuilder().setBrokerClient(brokerClient).build();
  }

  @Override
  protected void before() throws Throwable {
    gateway = new StubbedGateway(actorSchedulerRule.get(), brokerClient, longPollingHandler);
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
