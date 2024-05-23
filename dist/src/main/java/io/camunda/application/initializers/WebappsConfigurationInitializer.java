/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static io.camunda.application.Profile.IDENTITY_AUTH;
import static io.camunda.application.Profile.OPERATE;
import static io.camunda.application.Profile.SSO_AUTH;
import static io.camunda.application.Profile.TASKLIST;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.springframework.boot.DefaultPropertiesPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class WebappsConfigurationInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Set<String> WEBAPPS_PROFILES = Set.of(OPERATE.getId(), TASKLIST.getId());
  private static final Set<String> LOGIN_DELEGATED_PROFILES =
      Set.of(IDENTITY_AUTH.getId(), SSO_AUTH.getId());

  private static final String CAMUNDA_WEBAPPS_DEFAULT_APP_PROPERTY = "camunda.webapps.default-app";
  private static final String CAMUNDA_WEBAPPS_ENABLED_APPS_PROPERTY =
      "camunda.webapps.enabled-apps";
  private static final String CAMUNDA_WEBAPPS_LOGIN_DELEGATED_PROPERTY =
      "camunda.webapps.login-delegated";

  @Override
  public void initialize(final ConfigurableApplicationContext context) {
    final var environment = context.getEnvironment();
    final var propertySources = environment.getPropertySources();
    final var activeProfiles = Arrays.asList(environment.getActiveProfiles());

    final var propertyMap = new HashMap<String, Object>();
    final List<String> supportedApps =
        activeProfiles.stream().filter(WEBAPPS_PROFILES::contains).toList();
    propertyMap.put(CAMUNDA_WEBAPPS_ENABLED_APPS_PROPERTY, supportedApps);
    propertyMap.put(
        CAMUNDA_WEBAPPS_DEFAULT_APP_PROPERTY,
        supportedApps.contains(OPERATE.getId()) ? OPERATE.getId() : TASKLIST.getId());
    propertyMap.put(
        CAMUNDA_WEBAPPS_LOGIN_DELEGATED_PROPERTY,
        activeProfiles.stream().anyMatch(LOGIN_DELEGATED_PROFILES::contains));

    DefaultPropertiesPropertySource.addOrMerge(propertyMap, propertySources);
  }
}
