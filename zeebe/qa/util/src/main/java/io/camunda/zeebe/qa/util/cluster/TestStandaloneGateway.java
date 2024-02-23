/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.gateway.GatewayConfiguration.GatewayProperties;
import io.camunda.zeebe.gateway.StandaloneGateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.shared.Profile;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.function.Consumer;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Encapsulates an instance of the {@link StandaloneGateway} Spring application. */
public final class TestStandaloneGateway extends TestSpringApplication<TestStandaloneGateway>
    implements TestGateway<TestStandaloneGateway> {
  private final GatewayProperties config;

  public TestStandaloneGateway() {
    super(StandaloneGateway.class);
    config = new GatewayProperties();

    config.getNetwork().setHost("0.0.0.0");
    config.getNetwork().setPort(SocketUtil.getNextAddress().getPort());
    config.getCluster().setPort(SocketUtil.getNextAddress().getPort());

    //noinspection resource
    withBean("config", config, GatewayProperties.class);
  }

  @Override
  public TestStandaloneGateway self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(config.getCluster().getMemberId());
  }

  @Override
  public String host() {
    return config.getNetwork().getHost();
  }

  @Override
  public HealthActuator healthActuator() {
    return GatewayHealthActuator.ofAddress(monitoringAddress());
  }

  @Override
  public boolean isGateway() {
    return true;
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case GATEWAY -> config.getNetwork().getPort();
      case CLUSTER -> config.getCluster().getPort();
      default -> super.mappedPort(port);
    };
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    return super.createSpringBuilder().profiles(Profile.GATEWAY.getId());
  }

  @Override
  public TestStandaloneGateway withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(config);
    return self();
  }

  @Override
  public GatewayCfg gatewayConfig() {
    return config;
  }
}
