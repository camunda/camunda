/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.restore.RestoreApp;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Represents an instance of the {@link RestoreApp} Spring application. */
public final class TestRestoreApp extends TestSpringApplication<TestRestoreApp> {
  private final BrokerBasedProperties config;
  private long[] backupId;

  public TestRestoreApp() {
    this(new BrokerBasedProperties());
  }

  public TestRestoreApp(final BrokerBasedProperties config) {
    super(RestoreApp.class);
    this.config = config;

    //noinspection resource
    withBean("config", config, BrokerBasedProperties.class).withAdditionalProfile(Profile.RESTORE);
  }

  @Override
  public TestRestoreApp self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(String.valueOf(config.getCluster().getNodeId()));
  }

  @Override
  public HealthActuator healthActuator() {
    return new HealthActuator.NoopHealthActuator();
  }

  @Override
  public boolean isGateway() {
    return false;
  }

  @Override
  protected String[] commandLineArgs() {
    return backupId == null
        ? super.commandLineArgs()
        : new String[] {
          "--backupId="
              + Arrays.stream(backupId).mapToObj(Long::toString).collect(Collectors.joining(","))
        };
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    return super.createSpringBuilder().web(WebApplicationType.NONE);
  }

  public TestRestoreApp withBrokerConfig(final Consumer<BrokerCfg> modifier) {
    modifier.accept(config);
    return this;
  }

  public TestRestoreApp withBackupId(final long... backupId) {
    this.backupId = backupId;
    return this;
  }
}
