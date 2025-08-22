/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.commons.configuration;

public enum ConfigurationType {
  PROCESS("process"),
  TU_METRICS("tu-metrics"),
  METRICS("metrics"),
  TASKS("tasks");

  private final String configName;

  ConfigurationType(final String configName) {
    this.configName = configName;
  }

  /**
   * Static mapping for configuration names to class names depending on the Migrator.
   *
   * @return the appropriate {@link ConfigurationType} for the given Migrator class name.
   */
  public static ConfigurationType fromClassName(final String className) {
    return switch (className) {
      case "ProcessMigrator" -> PROCESS;
      case "OperateMetricMigrator" -> METRICS;
      case "TUMetricMigrator" -> TU_METRICS;
      case "TaskMigrator" -> TASKS;
      default -> throw new IllegalArgumentException("Unknown class name: " + className);
    };
  }
}
