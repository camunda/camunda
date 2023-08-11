/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster.spring;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.StandaloneBroker;
import io.camunda.zeebe.broker.shared.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.ZeebeBrokerNode;
import io.camunda.zeebe.qa.util.cluster.ZeebeGatewayNode;
import io.camunda.zeebe.qa.util.cluster.ZeebePort;
import io.camunda.zeebe.shared.Profile;
import java.nio.file.Path;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class SpringBrokerNode
    implements ZeebeBrokerNode<SpringBrokerNode>, ZeebeGatewayNode<SpringBrokerNode> {
  private final BrokerCfg config;
  private final SpringApplicationBuilder springBuilder;

  private ConfigurableApplicationContext springContext;

  public SpringBrokerNode(final BrokerCfg config, final SpringApplicationBuilder springBuilder) {
    this.config = config;
    this.springBuilder = springBuilder;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(String.valueOf(config.getCluster().getNodeId()));
  }

  @Override
  public String host() {
    return config.getNetwork().getHost();
  }

  @Override
  public void start() {
    if (isStarted()) {
      return;
    }

    springContext = springBuilder.run();
  }

  @Override
  public void shutdown() {
    if (springContext == null) {
      return;
    }

    springContext.close();
  }

  @Override
  public boolean isStarted() {
    return springContext != null && springContext.isActive();
  }

  @Override
  public int mappedPort(final ZeebePort port) {
    return switch (port) {
      case COMMAND -> config.getNetwork().getCommandApi().getPort();
      case GATEWAY -> config.getGateway().getNetwork().getPort();
      case CLUSTER -> config.getNetwork().getInternalApi().getPort();
      case MONITORING -> springContext
          .getEnvironment()
          .getProperty("server.port", int.class, port.port());
    };
  }

  @Override
  public String gatewayAddress() {
    if (!hasEmbeddedGateway()) {
      throw new IllegalStateException(
          "Expected to get the gateway address for this broker, but the embedded gateway is not enabled");
    }

    return ZeebeGatewayNode.super.gatewayAddress();
  }

  @Override
  public GatewayHealthActuator gatewayHealth() {
    throw new UnsupportedOperationException("Brokers do not support the gateway health indicators");
  }

  @Override
  public ZeebeClientBuilder newClientBuilder() {
    final var builder = ZeebeClient.newClientBuilder().gatewayAddress(gatewayAddress());
    final var security = config.getGateway().getSecurity();
    if (security.isEnabled()) {
      builder.caCertificatePath(security.getCertificateChainPath().getAbsolutePath());
    } else {
      builder.usePlaintext();
    }

    return builder;
  }

  @Override
  public boolean hasEmbeddedGateway() {
    return config.getGateway().isEnable();
  }

  @Override
  public HealthActuator healthActuator() {
    return brokerHealth();
  }

  public static final class Builder
      extends AbstractSpringBuilder<SpringBrokerNode, BrokerCfg, Builder> {

    private WorkingDirectory workingDirectory;

    public Builder() {
      super(new BrokerCfg());
    }

    public Builder withWorkingDirectory(final Path path) {
      workingDirectory = new WorkingDirectory(path, false);
      return this;
    }

    @Override
    protected SpringBrokerNode createNode(final SpringApplicationBuilder builder) {
      builder.profiles(Profile.BROKER.getId()).sources(StandaloneBroker.class);

      return new SpringBrokerNode(config, builder);
    }
  }
}
