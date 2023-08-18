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
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestBroker;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.ZeebePort;
import io.camunda.zeebe.qa.util.cluster.spring.ContextOverrideInitializer.Bean;
import io.camunda.zeebe.shared.Profile;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Represents an instance of the {@link StandaloneBroker} Spring application. */
@SuppressWarnings("UnusedReturnValue")
public final class TestStandaloneBroker
    implements TestBroker<TestStandaloneBroker>, TestGateway<TestStandaloneBroker> {
  private final BrokerCfg config;
  private final SpringApplicationBuilder springBuilder;
  private final Map<String, Bean<?>> beans;
  private final Map<String, Object> propertyOverrides;

  private ConfigurableApplicationContext springContext;

  public TestStandaloneBroker() {
    this(new BrokerCfg(), new HashMap<>(), new HashMap<>());
  }

  private TestStandaloneBroker(
      final BrokerCfg config,
      final Map<String, Bean<?>> beans,
      final Map<String, Object> propertyOverrides) {
    this(
        config,
        beans,
        propertyOverrides,
        TestSupport.defaultSpringBuilder(beans, propertyOverrides));
  }

  private TestStandaloneBroker(
      final BrokerCfg config,
      final Map<String, Bean<?>> beans,
      final Map<String, Object> propertyOverrides,
      final SpringApplicationBuilder springBuilder) {
    this.config = config;
    this.beans = beans;
    this.propertyOverrides = propertyOverrides;
    this.springBuilder = springBuilder;

    springBuilder.profiles(Profile.BROKER.getId(), "test").sources(StandaloneBroker.class);
    config.getNetwork().getCommandApi().setPort(SocketUtil.getNextAddress().getPort());
    config.getNetwork().getInternalApi().setPort(SocketUtil.getNextAddress().getPort());
    config.getGateway().getNetwork().setPort(SocketUtil.getNextAddress().getPort());
    propertyOverrides.put("server.port", SocketUtil.getNextAddress().getPort());

    withBean("uninitializedBrokerCfg", config, BrokerCfg.class);
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
      case MONITORING -> Optional.ofNullable(propertyOverrides.get("server.port"))
          .map(Integer.class::cast)
          .orElseGet(
              () -> springContext.getEnvironment().getProperty("server.port", Integer.class));
    };
  }

  @Override
  public TestStandaloneBroker withEnv(final String key, final Object value) {
    propertyOverrides.put(key, value);
    return this;
  }

  @Override
  public String gatewayAddress() {
    if (!hasEmbeddedGateway()) {
      throw new IllegalStateException(
          "Expected to get the gateway address for this broker, but the embedded gateway is not enabled");
    }

    return TestGateway.super.gatewayAddress();
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
  public GatewayCfg gatewayConfig() {
    return config.getGateway();
  }

  @Override
  public boolean hasEmbeddedGateway() {
    return config.getGateway().isEnable();
  }

  @Override
  public HealthActuator healthActuator() {
    return brokerHealth();
  }

  @Override
  public BrokerCfg brokerConfig() {
    return config;
  }

  public TestStandaloneBroker withBrokerConfig(final Consumer<BrokerCfg> modifier) {
    modifier.accept(config);
    return this;
  }

  public <T> TestStandaloneBroker withBean(
      final String qualifier, final T bean, final Class<T> type) {
    beans.put(qualifier, new Bean<>(bean, type));
    return this;
  }
}
