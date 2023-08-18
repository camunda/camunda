/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster.spring;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.gateway.StandaloneGateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.cluster.ZeebeGateway;
import io.camunda.zeebe.qa.util.cluster.ZeebePort;
import io.camunda.zeebe.shared.Profile;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Encapsulates an instance of the {@link StandaloneGateway} Spring application. */
public final class SpringGateway implements ZeebeGateway<SpringGateway> {
  private final GatewayCfg config;
  private final SpringApplicationBuilder springBuilder;

  private ConfigurableApplicationContext springContext;

  public SpringGateway(final GatewayCfg config, final SpringApplicationBuilder springBuilder) {
    this.config = config;
    this.springBuilder = springBuilder;
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
      case GATEWAY -> config.getNetwork().getPort();
      case CLUSTER -> config.getCluster().getPort();
      case MONITORING -> springContext
          .getEnvironment()
          .getProperty("server.port", int.class, port.port());
      default -> throw new IllegalStateException("Unexpected value: " + port);
    };
  }

  @Override
  public ZeebeClientBuilder newClientBuilder() {
    final var builder = ZeebeClient.newClientBuilder().gatewayAddress(gatewayAddress());
    final var security = config.getSecurity();
    if (security.isEnabled()) {
      builder.caCertificatePath(security.getCertificateChainPath().getAbsolutePath());
    } else {
      builder.usePlaintext();
    }

    return builder;
  }

  public static final class Builder
      extends AbstractSpringBuilder<SpringGateway, GatewayCfg, Builder> {

    public Builder() {
      super(new GatewayCfg());
    }

    @Override
    protected SpringGateway createNode(final SpringApplicationBuilder builder) {
      builder.profiles(Profile.GATEWAY.getId()).sources(StandaloneGateway.class);

      return new SpringGateway(config, builder);
    }
  }
}
