/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.util;

import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.rules.ExternalResource;

public final class StubbedGatewayRule extends ExternalResource {

  protected StubbedGateway gateway;
  protected GatewayBlockingStub client;
  private final ActorSchedulerRule actorSchedulerRule;
  private final GatewayCfg config;
  private final StubbedBrokerClient brokerClient;

  public StubbedGatewayRule(final ActorSchedulerRule actorSchedulerRule, final GatewayCfg config) {
    this.actorSchedulerRule = actorSchedulerRule;
    brokerClient = new StubbedBrokerClient();
    this.config = config;
  }

  @Override
  protected void before() throws Throwable {
    gateway = new StubbedGateway(actorSchedulerRule.get(), brokerClient, config);
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
