/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static io.camunda.application.Profile.CONSOLIDATED_AUTH;
import static io.camunda.application.Profile.getWebappProfiles;

import io.camunda.spring.utils.DatabaseTypeUtils;
import java.util.HashMap;
import java.util.Set;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Bridges legacy authentication properties to the new auth library properties and adds the
 * "consolidated-auth" profile if it's not set.
 */
public class DefaultAuthenticationInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final String OLD_AUTH_METHOD = "camunda.security.authentication.method";
  private static final String NEW_AUTH_METHOD = "camunda.auth.method";

  @Override
  public void initialize(final ConfigurableApplicationContext context) {
    final var env = context.getEnvironment();
    bridgeAuthMethodProperty(context);
    if (shouldApplyDefaultAuthenticationProfile(env)) {
      env.addActiveProfile(CONSOLIDATED_AUTH.getId());
    }
    bridgeSecondaryStorageProperty(context);
  }

  /**
   * If the old property {@code camunda.security.authentication.method} is set but the new property
   * {@code camunda.auth.method} is not, bridge the old value to the new property so the auth
   * library auto-configurations activate.
   */
  private void bridgeAuthMethodProperty(final ConfigurableApplicationContext context) {
    final var env = context.getEnvironment();
    if (env.getProperty(NEW_AUTH_METHOD) == null && env.getProperty(OLD_AUTH_METHOD) != null) {
      final var propertyMap = new HashMap<String, Object>();
      propertyMap.put(NEW_AUTH_METHOD, env.getProperty(OLD_AUTH_METHOD));
      DefaultPropertiesPropertySource.addOrMerge(propertyMap, env.getPropertySources());
    }
  }

  private void bridgeSecondaryStorageProperty(final ConfigurableApplicationContext context) {
    final var env = context.getEnvironment();
    final var propertyMap = new HashMap<String, Object>();
    propertyMap.put(
        "camunda.auth.basic.secondary-storage-available",
        String.valueOf(DatabaseTypeUtils.isSecondaryStorageEnabled(env)));
    DefaultPropertiesPropertySource.addOrMerge(propertyMap, env.getPropertySources());
  }

  protected boolean shouldApplyDefaultAuthenticationProfile(final Environment environment) {
    if (environment.getProperty(OLD_AUTH_METHOD) != null
        || environment.getProperty(NEW_AUTH_METHOD) != null) {
      return false;
    }
    final Set<String> activeProfiles = Set.of(environment.getActiveProfiles());
    return webappProfileIsPresent(activeProfiles)
        && !consolidatedAuthProfileIsPresent(activeProfiles);
  }

  private boolean webappProfileIsPresent(final Set<String> activeProfiles) {
    return getWebappProfiles().stream()
        .anyMatch(profile -> activeProfiles.contains(profile.getId()));
  }

  private boolean consolidatedAuthProfileIsPresent(final Set<String> activeProfiles) {
    return activeProfiles.contains(CONSOLIDATED_AUTH.getId());
  }
}
