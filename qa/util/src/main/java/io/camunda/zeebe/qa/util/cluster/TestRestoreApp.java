/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator.NoopHealthActuator;
import io.camunda.zeebe.qa.util.cluster.spring.ContextOverrideInitializer.Bean;
import io.camunda.zeebe.restore.RestoreApp;
import io.camunda.zeebe.shared.Profile;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.context.ConfigurableApplicationContext;

public final class TestRestoreApp implements TestStandalone<TestRestoreApp> {
  private final BrokerCfg config;
  private final Map<String, Bean<?>> beans;
  private final Map<String, Object> propertyOverrides;

  private ConfigurableApplicationContext springContext;
  private long backupId;

  public TestRestoreApp() {
    this(new BrokerCfg());
  }

  public TestRestoreApp(final BrokerCfg config) {
    this(config, new HashMap<>(), new HashMap<>());
  }

  private TestRestoreApp(
      final BrokerCfg config,
      final Map<String, Bean<?>> beans,
      final Map<String, Object> propertyOverrides) {
    this.config = config;
    this.beans = beans;
    this.propertyOverrides = propertyOverrides;

    propertyOverrides.put("server.port", SocketUtil.getNextAddress().getPort());
    withBean("config", config, BrokerCfg.class);
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
  public TestRestoreApp start() {
    if (!isStarted()) {
      final var builder =
          TestSupport.defaultSpringBuilder(beans, propertyOverrides)
              .profiles(Profile.RESTORE.getId(), "test")
              .sources(RestoreApp.class);
      springContext = builder.run("--backupId=" + backupId);
    }

    return this;
  }

  @Override
  public TestRestoreApp stop() {
    if (springContext != null) {
      springContext.close();
      springContext = null;
    }

    return this;
  }

  @Override
  public HealthActuator healthActuator() {
    // the restore app is a one shot app, so it exits once Spring is finished
    return new NoopHealthActuator();
  }

  @Override
  public boolean isStarted() {
    return springContext != null && springContext.isActive();
  }

  @Override
  public int mappedPort(final ZeebePort port) {
    if (port != ZeebePort.MONITORING) {
      throw new IllegalStateException("Unexpected value: " + port);
    }

    return TestSupport.monitoringPort(springContext, propertyOverrides);
  }

  @Override
  public TestRestoreApp withEnv(final String key, final Object value) {
    propertyOverrides.put(key, value);
    return this;
  }

  @Override
  public boolean isGateway() {
    return false;
  }

  @Override
  public TestRestoreApp self() {
    return this;
  }

  @Override
  public <T> TestRestoreApp withBean(final String qualifier, final T bean, final Class<T> type) {
    beans.put(qualifier, new Bean<>(bean, type));
    return this;
  }

  @Override
  public <T> T bean(final Class<T> type) {
    return springContext.getBean(type);
  }

  public TestRestoreApp withBrokerConfig(final Consumer<BrokerCfg> modifier) {
    modifier.accept(config);
    return this;
  }

  public TestRestoreApp withBackupId(final long backupId) {
    this.backupId = backupId;
    return this;
  }
}
