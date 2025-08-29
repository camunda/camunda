/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.StandalonePrefixMigration;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.restore.RestoreApp;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Represents an instance of the {@link RestoreApp} Spring application. */
public final class TestPrefixMigrationApp extends TestSpringApplication<TestPrefixMigrationApp> {

  public TestPrefixMigrationApp(
      final SearchEngineConnectProperties connectConfiguration,
      final TasklistProperties tasklistProperties,
      final OperateProperties operateProperties) {
    super(StandalonePrefixMigration.class, SearchEngineDatabaseConfiguration.class);

    withBean("connectConfiguration", connectConfiguration, SearchEngineConnectProperties.class)
        .withBean("tasklistProperties", tasklistProperties, TasklistProperties.class)
        .withBean("operateProperties", operateProperties, OperateProperties.class);
  }

  @Override
  public TestPrefixMigrationApp self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from("prefix-migration");
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
