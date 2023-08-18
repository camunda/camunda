/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.gateway.StandaloneGateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.cluster.spring.ContextOverrideInitializer.Bean;
import io.camunda.zeebe.shared.Profile;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.context.ConfigurableApplicationContext;

/** Encapsulates an instance of the {@link StandaloneGateway} Spring application. */
public final class TestStandaloneGateway implements TestGateway<TestStandaloneGateway> {
  private final GatewayCfg config;
  private final Map<String, Bean<?>> beans;
  private final Map<String, Object> propertyOverrides;

  private ConfigurableApplicationContext springContext;

  public TestStandaloneGateway() {
    this(new GatewayCfg());
  }

  public TestStandaloneGateway(final GatewayCfg config) {
    this(config, new HashMap<>(), new HashMap<>());
  }

  private TestStandaloneGateway(
      final GatewayCfg config,
      final Map<String, Bean<?>> beans,
      final Map<String, Object> propertyOverrides) {
    this.config = config;
    this.beans = beans;
    this.propertyOverrides = propertyOverrides;

    config.getNetwork().setPort(SocketUtil.getNextAddress().getPort());
    config.getCluster().setPort(SocketUtil.getNextAddress().getPort());
    propertyOverrides.put("server.port", SocketUtil.getNextAddress().getPort());
    withBean("config", config, GatewayCfg.class);
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
  public TestStandaloneGateway start() {
    if (!isStarted()) {
      final var builder =
          TestSupport.defaultSpringBuilder(beans, propertyOverrides)
              .profiles(Profile.GATEWAY.getId(), "test")
              .sources(StandaloneGateway.class);
      springContext = builder.run();
    }

    return this;
  }

  @Override
  public TestStandaloneGateway stop() {
    if (springContext != null) {
      springContext.close();
      springContext = null;
    }

    return this;
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
      case MONITORING -> TestSupport.monitoringPort(springContext, propertyOverrides);
      default -> throw new IllegalStateException("Unexpected value: " + port);
    };
  }

  @Override
  public TestStandaloneGateway withEnv(final String key, final Object value) {
    propertyOverrides.put(key, value);
    return this;
  }

  @Override
  public boolean isGateway() {
    return true;
  }

  @Override
  public TestStandaloneGateway self() {
    return this;
  }

  @Override
  public <T> TestStandaloneGateway withBean(
      final String qualifier, final T bean, final Class<T> type) {
    beans.put(qualifier, new Bean<>(bean, type));
    return this;
  }

  @Override
  public <T> T bean(final Class<T> type) {
    return springContext.getBean(type);
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

  public TestStandaloneGateway withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(config);
    return this;
  }
}
