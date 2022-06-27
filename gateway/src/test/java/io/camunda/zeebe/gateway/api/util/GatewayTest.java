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
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;

public abstract class GatewayTest {

  public final ControlledActorClock actorClock;
  public final ActorSchedulerRule actorSchedulerRule;
  public final StubbedGatewayRule gatewayRule;
  @Rule public RuleChain ruleChain;
  protected StubbedGateway gateway;
  protected GatewayBlockingStub client;
  protected StubbedBrokerClient brokerClient;

  public GatewayTest() {
    this(new GatewayCfg());
  }

  public GatewayTest(final GatewayCfg config) {
    actorClock = new ControlledActorClock();
    actorSchedulerRule = new ActorSchedulerRule(actorClock);
    gatewayRule = new StubbedGatewayRule(actorSchedulerRule, config);
    ruleChain = RuleChain.outerRule(actorSchedulerRule).around(gatewayRule);
  }

  @Before
  public void setUp() {
    gateway = gatewayRule.getGateway();
    client = gatewayRule.getClient();
    brokerClient = gatewayRule.getBrokerClient();
  }

  @After
  public void tearDown() {
    // nothing to do
  }
}
