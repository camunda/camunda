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
import io.camunda.configuration.Camunda;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator.NoopHealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import java.util.function.Consumer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class TestStandaloneSchemaManager
    extends TestSpringApplication<TestStandaloneSchemaManager> {
  public TestStandaloneSchemaManager() {
    super(StandaloneSchemaManager.class);
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

  @Override
  public TestStandaloneSchemaManager withUnifiedConfig(final Consumer<Camunda> modifier) {
    throw new UnsupportedOperationException(
        "TestStandaloneSchemaManager does not support unified configuration");
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    return super.createSpringBuilder().web(WebApplicationType.NONE);
  }
}
