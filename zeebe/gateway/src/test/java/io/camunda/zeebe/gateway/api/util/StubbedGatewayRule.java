/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.util;

import io.camunda.zeebe.gateway.api.util.StubbedGateway.StubbedJobStreamer;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import org.junit.rules.ExternalResource;

public final class StubbedGatewayRule extends ExternalResource {

  protected StubbedGateway gateway;
  protected GatewayBlockingStub client;
  protected GatewayStub asyncClient;
  private final ActorSchedulerRule actorSchedulerRule;
  private final GatewayCfg config;
  private final StubbedBrokerClient brokerClient;
  private final StubbedJobStreamer jobStreamer;

  public StubbedGatewayRule(final ActorSchedulerRule actorSchedulerRule, final GatewayCfg config) {
    this.actorSchedulerRule = actorSchedulerRule;
    brokerClient = new StubbedBrokerClient();
    jobStreamer = new StubbedJobStreamer();
    this.config = config;
  }

  @Override
  protected void before() throws Throwable {
    gateway = new StubbedGateway(actorSchedulerRule.get(), brokerClient, jobStreamer, config);
    gateway.start();
    client = gateway.buildClient();
    asyncClient = gateway.buildAsyncClient();
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

  public GatewayStub getAsyncClient() {
    return asyncClient;
  }

  public StubbedBrokerClient getBrokerClient() {
    return brokerClient;
  }

  public StubbedJobStreamer getJobStreamer() {
    return jobStreamer;
  }
}
