/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.util;

import io.camunda.zeebe.gateway.api.util.StubbedGateway.StubbedJobStreamer;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
  protected GatewayStub asyncClient;
  protected StubbedBrokerClient brokerClient;
  protected StubbedJobStreamer jobStreamer;

  public GatewayTest(final GatewayCfg config) {
    actorClock = new ControlledActorClock();
    actorSchedulerRule = new ActorSchedulerRule(actorClock);
    gatewayRule = new StubbedGatewayRule(actorSchedulerRule, config);
    ruleChain = RuleChain.outerRule(actorSchedulerRule).around(gatewayRule);
  }

  public GatewayTest() {
    this(new GatewayCfg());
  }

  private GatewayTest(final Supplier<GatewayCfg> configSupplier) {
    this(configSupplier.get());
  }

  public GatewayTest(final Consumer<GatewayCfg> modifier) {
    this(
        () -> {
          final GatewayCfg config = new GatewayCfg();
          modifier.accept(config);
          return config;
        });
  }

  @Before
  public void setUp() {
    gateway = gatewayRule.getGateway();
    client = gatewayRule.getClient();
    asyncClient = gatewayRule.getAsyncClient();
    brokerClient = gatewayRule.getBrokerClient();
    jobStreamer = gatewayRule.getJobStreamer();
  }

  @After
  public void tearDown() {
    // nothing to do
  }
}
