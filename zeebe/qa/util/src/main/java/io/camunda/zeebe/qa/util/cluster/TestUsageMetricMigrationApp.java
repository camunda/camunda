/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.StandaloneUsageMetricMigration;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineConnectProperties;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Represents an instance of the {@link StandaloneUsageMetricMigration} Spring application. */
public final class TestUsageMetricMigrationApp
    extends TestSpringApplication<TestUsageMetricMigrationApp> {

  public TestUsageMetricMigrationApp(final SearchEngineConnectProperties connectConfiguration) {
    super(StandaloneUsageMetricMigration.class, SearchEngineDatabaseConfiguration.class);

    withBean("connectConfiguration", connectConfiguration, SearchEngineConnectProperties.class);
  }

  @Override
  public TestUsageMetricMigrationApp self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from("usage-metric-migration");
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
  protected SpringApplicationBuilder createSpringBuilder() {
    return super.createSpringBuilder().web(WebApplicationType.NONE);
  }
}
