/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.util;

import io.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;

public class GatewayTest {

  protected final ControlledActorClock actorClock = new ControlledActorClock();
  public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(actorClock);
  public final StubbedGatewayRule gatewayRule = new StubbedGatewayRule(actorSchedulerRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(gatewayRule);

  protected StubbedGateway gateway;
  protected GatewayBlockingStub client;
  protected StubbedBrokerClient brokerClient;

  @Before
  public void setUp() {
    gateway = gatewayRule.getGateway();
    client = gatewayRule.getClient();
    brokerClient = gatewayRule.getBrokerClient();
  }
}
