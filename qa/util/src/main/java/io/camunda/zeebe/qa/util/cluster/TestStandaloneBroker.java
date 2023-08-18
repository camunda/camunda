/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.StandaloneBroker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.spring.ContextOverrideInitializer.Bean;
import io.camunda.zeebe.shared.Profile;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.context.ConfigurableApplicationContext;

/** Represents an instance of the {@link StandaloneBroker} Spring application. */
@SuppressWarnings("UnusedReturnValue")
public final class TestStandaloneBroker
    implements TestGateway<TestStandaloneBroker>, TestStandalone<TestStandaloneBroker> {

  private static final String RECORDING_EXPORTER_ID = "recordingExporter";
  private final BrokerCfg config;
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
    this.config = config;
    this.beans = beans;
    this.propertyOverrides = propertyOverrides;

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
  public TestStandaloneBroker start() {
    if (!isStarted()) {
      final var builder =
          TestSupport.defaultSpringBuilder(beans, propertyOverrides)
              .profiles(Profile.BROKER.getId(), "test")
              .sources(StandaloneBroker.class);
      springContext = builder.run();
    }

    return this;
  }

  @Override
  public TestStandaloneBroker stop() {
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
      case COMMAND -> config.getNetwork().getCommandApi().getPort();
      case GATEWAY -> config.getGateway().getNetwork().getPort();
      case CLUSTER -> config.getNetwork().getInternalApi().getPort();
      case MONITORING -> TestSupport.monitoringPort(springContext, propertyOverrides);
    };
  }

  @Override
  public TestStandaloneBroker withEnv(final String key, final Object value) {
    propertyOverrides.put(key, value);
    return this;
  }

  @Override
  public boolean isGateway() {
    return hasEmbeddedGateway();
  }

  @Override
  public TestStandaloneBroker self() {
    return this;
  }

  @Override
  public <T> TestStandaloneBroker withBean(
      final String qualifier, final T bean, final Class<T> type) {
    beans.put(qualifier, new Bean<>(bean, type));
    return this;
  }

  @Override
  public <T> T bean(final Class<T> type) {
    return springContext.getBean(type);
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
  public HealthActuator healthActuator() {
    return brokerHealth();
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

  public boolean hasEmbeddedGateway() {
    return config.getGateway().isEnable();
  }

  public BrokerCfg brokerConfig() {
    return config;
  }

  public TestStandaloneBroker withBrokerConfig(final Consumer<BrokerCfg> modifier) {
    modifier.accept(config);
    return this;
  }

  /**
   * Returns the health actuator for this broker. You can use this to check for liveness, readiness,
   * and startup.
   */
  public BrokerHealthActuator brokerHealth() {
    return BrokerHealthActuator.ofAddress(monitoringAddress());
  }

  public TestStandaloneBroker withRecordingExporter(final boolean useRecordingExporter) {
    if (!useRecordingExporter) {
      config.getExporters().remove(RECORDING_EXPORTER_ID);
    } else {
      final var exporterConfig = new ExporterCfg();
      exporterConfig.setClassName(RecordingExporter.class.getName());
      config.getExporters().put(RECORDING_EXPORTER_ID, exporterConfig);
    }

    return this;
  }
}
