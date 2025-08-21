/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.commons.configuration;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camunda")
public class MigrationProperties {

  private Map<ConfigurationType, MigrationConfiguration> migration = new HashMap<>();

  public Map<ConfigurationType, MigrationConfiguration> getMigration() {
    return migration;
  }

  public void setMigration(final Map<ConfigurationType, MigrationConfiguration> migration) {
    this.migration = migration;
  }

  public MigrationConfiguration getMigrationConfiguration(final Class<?> clazz) {
    return migration.getOrDefault(
        ConfigurationType.fromClassName(clazz.getSimpleName()), new MigrationConfiguration());
  }
}
