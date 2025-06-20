/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;

public class FallbackConfig {

  static Environment environment;

  public static boolean getBoolean(final String legacyBreadcrumb, final boolean defaultValue) {
    final String deprecated = getDeprecatedValue(legacyBreadcrumb);
    if (deprecated != null) {
      return Boolean.parseBoolean(deprecated);
    }

    return defaultValue;
  }

  public static int getInt(final String legacyBreadcrumb, final int defaultValue) {
    final String deprecated = getDeprecatedValue(legacyBreadcrumb);
    if (deprecated != null) {
      return Integer.parseInt(deprecated);
    }

    return defaultValue;
  }

  public static String getString(final String legacyBreadcrumb, final String defaultValue) {
    final String deprecated = getDeprecatedValue(legacyBreadcrumb);
    if (deprecated != null) {
      return deprecated;
    }

    return defaultValue;
  }

  public static DataSize getDataSize(final String legacyBreadcrumb, final DataSize defaultValue) {
    final String deprecated = getDeprecatedValue(legacyBreadcrumb);
    if (deprecated != null) {
      return DataSize.parse(deprecated);
    }

    return defaultValue;
  }

  public static Duration getDuration(final String legacyBreadcrumb, final Duration defaultValue) {
    final String deprecated = getDeprecatedValue(legacyBreadcrumb);
    if (deprecated != null) {
      return Duration.parse(deprecated);
    }

    return defaultValue;
  }

  private static String getDeprecatedValue(final String legacyBreadcrumb) {
    if (environment == null) {
      return null;
    }

    return environment.getProperty(legacyBreadcrumb);
  }
}
