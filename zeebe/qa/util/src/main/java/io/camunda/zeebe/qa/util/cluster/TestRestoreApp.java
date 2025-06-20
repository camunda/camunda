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
import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.unifiedconfig.UnifiedConfiguration;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.restore.RestoreApp;
import java.util.function.BiConsumer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Represents an instance of the {@link RestoreApp} Spring application. */
public final class TestRestoreApp extends TestSpringApplication<TestRestoreApp> {
  private final BrokerBasedProperties brokerBasedProperties;
  private final UnifiedConfiguration unifiedConfiguration;
  private Long backupId;

  public TestRestoreApp() {
    this(new BrokerBasedProperties(), new UnifiedConfiguration());
  }

  public TestRestoreApp(
      final BrokerBasedProperties brokerBasedProperties,
      final UnifiedConfiguration unifiedConfiguration) {
    super(RestoreApp.class);
    this.brokerBasedProperties = brokerBasedProperties;
    this.unifiedConfiguration = unifiedConfiguration;

    //noinspection resource
    withBean("config", brokerBasedProperties, BrokerBasedProperties.class)
        .withAdditionalProfile(Profile.RESTORE);

    withBean("UnifiedConfiguration", unifiedConfiguration, UnifiedConfiguration.class)
        .withAdditionalProfile(Profile.RESTORE);
  }

  @Override
  public TestRestoreApp self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    if (unifiedConfiguration.getCluster().getNodeId() != 0) {
      // using the new unified configuration only if explicitly used/
      // NOTE: 0 is the default value.
      return MemberId.from(String.valueOf(unifiedConfiguration.getCluster().getNodeId()));
    }

    // fallback to the legacy mechanism otherwise.
    return MemberId.from(String.valueOf(brokerBasedProperties.getCluster().getNodeId()));
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
    return backupId == null ? super.commandLineArgs() : new String[] {"--backupId=" + backupId};
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    return super.createSpringBuilder().web(WebApplicationType.NONE);
  }

  public TestRestoreApp withBrokerConfig(
      final BiConsumer<BrokerCfg, UnifiedConfiguration> modifier) {
    // TODO: Revert the signature to Consumer once BrokerCfg is no longer used,
    //  in favor of UnifiedConfiguration
    modifier.accept(brokerBasedProperties, unifiedConfiguration);
    return this;
  }

  public TestRestoreApp withBackupId(final long backupId) {
    this.backupId = backupId;
    return this;
  }
}
