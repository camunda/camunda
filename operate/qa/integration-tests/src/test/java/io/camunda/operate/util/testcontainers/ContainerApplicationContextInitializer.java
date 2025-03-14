/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.testcontainers;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.search.connect.configuration.DatabaseType;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class ContainerApplicationContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  @Override
  public void initialize(final ConfigurableApplicationContext applicationContext) {
    switch (DatabaseInfo.getCurrent()) {
      case DatabaseType.ELASTICSEARCH ->
          new ElasticsearchContainerApplicationContextInitializer().initialize(applicationContext);
      case DatabaseType.OPENSEARCH ->
          new OpensearchContainerApplicationContextInitializer().initialize(applicationContext);
      default ->
          throw new IllegalArgumentException("Unsupported database " + DatabaseInfo.getCurrent());
    }
  }
}
