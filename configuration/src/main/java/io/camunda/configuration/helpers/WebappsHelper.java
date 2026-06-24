/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.helpers;

import org.springframework.core.env.ConfigurableEnvironment;

public class WebappsHelper {
  private static final String OPERATE = "operate";
  private static final String TASKLIST = "tasklist";
  private static final String IDENTITY = "identity";
  private static final String ADMIN = "admin";

  public static boolean isOperateEnabled(final ConfigurableEnvironment environment) {
    return isWebappEnabled(environment, OPERATE);
  }

  public static boolean isTasklistEnabled(final ConfigurableEnvironment environment) {
    return isWebappEnabled(environment, TASKLIST);
  }

  public static boolean isIdentityEnabled(final ConfigurableEnvironment environment) {
    return isWebappEnabled(environment, IDENTITY) && isWebappEnabled(environment, ADMIN);
  }

  public static boolean isOperateUiEnabled(final ConfigurableEnvironment environment) {
    return isWebappUiEnabled(environment, OPERATE);
  }

  public static boolean isTasklistUiEnabled(final ConfigurableEnvironment environment) {
    return isWebappUiEnabled(environment, TASKLIST);
  }

  public static boolean isIdentityUiEnabled(final ConfigurableEnvironment environment) {
    return isWebappUiEnabled(environment, IDENTITY) && isWebappUiEnabled(environment, ADMIN);
  }

  private static boolean isWebappEnabled(
      final ConfigurableEnvironment environment, final String webappName) {
    return environment.getProperty("camunda." + webappName + ".webappEnabled", Boolean.class, true)
        && environment.getProperty(
            "camunda.webapps." + webappName + ".enabled", Boolean.class, true);
  }

  private static boolean isWebappUiEnabled(
      final ConfigurableEnvironment environment, final String webapp) {
    return isWebappEnabled(environment, webapp)
        && environment.getProperty(
            "camunda.webapps." + webapp + ".ui-enabled", Boolean.class, true);
  }
}
