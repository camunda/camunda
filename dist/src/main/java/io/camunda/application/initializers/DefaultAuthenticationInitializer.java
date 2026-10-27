/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static io.camunda.application.Profile.CONSOLIDATED_AUTH;
import static io.camunda.application.Profile.INSECURE;
import static io.camunda.application.Profile.getWebappProfiles;

import io.camunda.authentication.config.AuthenticationProperties;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

/** Adds the "consolidated-auth" profile if it's not set */
public class DefaultAuthenticationInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(final ConfigurableApplicationContext context) {
    final var env = context.getEnvironment();
    if (shouldApplyDefaultAuthenticationProfile(env)) {
      env.addActiveProfile(CONSOLIDATED_AUTH.getId());
    }

    final Set<String> activeProfiles = Set.of(env.getActiveProfiles());
    if (activeProfiles.contains(INSECURE.getId())) {
      final MutablePropertySources propertySources = env.getPropertySources();
      final Map<String, Object> propertyMap =
          Map.of(
              "zeebe.broker.gateway.security.enabled", false, // embedded gateway
              "zeebe.gateway.security.enabled", false, // dedicated gateway
              "camunda.security.authentication.unprotected-api", true,
              "camunda.security.authorizations.enabled", false);
      DefaultPropertiesPropertySource.addOrMerge(propertyMap, propertySources);
    }
  }

  protected boolean shouldApplyDefaultAuthenticationProfile(final Environment environment) {
    if (environment.getProperty(AuthenticationProperties.METHOD) != null) {
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
