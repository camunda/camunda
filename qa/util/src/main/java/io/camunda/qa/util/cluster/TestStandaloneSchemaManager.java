/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.StandaloneSchemaManager;
import io.camunda.application.commons.configuration.UnifiedConfigurationModule;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator.NoopHealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class TestStandaloneSchemaManager
    extends TestSpringApplication<TestStandaloneSchemaManager> {

  public TestStandaloneSchemaManager() {
    super(UnifiedConfigurationModule.class, StandaloneSchemaManager.class);
  }

  @Override
  public TestStandaloneSchemaManager self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from("schema");
  }

  @Override
  public HealthActuator healthActuator() {
    return new NoopHealthActuator();
  }

  @Override
  public boolean isGateway() {
    return false;
  }

  /**
   * Convenience method for setting the secondary storage type in the unified configuration.
   *
   * @param type the secondary storage type
   * @return itself for chaining
   */
  public TestStandaloneSchemaManager withSecondaryStorageType(final SecondaryStorageType type) {
    unifiedConfig.getData().getSecondaryStorage().setType(type);
    return this;
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    return super.createSpringBuilder().web(WebApplicationType.NONE);
  }
}
