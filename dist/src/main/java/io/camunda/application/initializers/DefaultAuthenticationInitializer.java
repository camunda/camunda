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

import io.camunda.auth.domain.config.AuthenticationConfiguration;
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
    bridgePersistenceProperties(context);
    bridgeAuthenticationProperties(context);
  }

  /**
   * Ensures the new property {@code camunda.auth.method} is always set so the auth library
   * auto-configurations activate. If the old property {@code
   * camunda.security.authentication.method} is set, bridges its value. Otherwise, sets the default
   * authentication method.
   */
  private void bridgeAuthMethodProperty(final ConfigurableApplicationContext context) {
    final var env = context.getEnvironment();
    if (env.getProperty(NEW_AUTH_METHOD) != null) {
      return;
    }
    final var propertyMap = new HashMap<String, Object>();
    if (env.getProperty(OLD_AUTH_METHOD) != null) {
      propertyMap.put(NEW_AUTH_METHOD, env.getProperty(OLD_AUTH_METHOD));
    } else {
      propertyMap.put(
          NEW_AUTH_METHOD, AuthenticationConfiguration.DEFAULT_METHOD.name().toLowerCase());
    }
    DefaultPropertiesPropertySource.addOrMerge(propertyMap, env.getPropertySources());
  }

  private void bridgeSecondaryStorageProperty(final ConfigurableApplicationContext context) {
    final var env = context.getEnvironment();
    final var propertyMap = new HashMap<String, Object>();
    propertyMap.put(
        "camunda.auth.basic.secondary-storage-available",
        String.valueOf(DatabaseTypeUtils.isSecondaryStorageEnabled(env)));
    DefaultPropertiesPropertySource.addOrMerge(propertyMap, env.getPropertySources());
  }

  /**
   * Bridges the monorepo's secondary storage type to the auth library's persistence properties.
   *
   * <p>Maps {@code camunda.data.secondary-storage.type} (elasticsearch|rdbms) to {@code
   * camunda.auth.persistence.type} and sets {@code camunda.auth.persistence.mode=external} because
   * the Orchestration Cluster uses Zeebe as the source of truth for identity data — the auth
   * library only reads from secondary storage.
   */
  private void bridgePersistenceProperties(final ConfigurableApplicationContext context) {
    final var env = context.getEnvironment();
    if (env.getProperty("camunda.auth.persistence.type") != null) {
      return;
    }
    final var propertyMap = new HashMap<String, Object>();
    final var storageType = DatabaseTypeUtils.getDatabaseTypeOrDefault(env);
    propertyMap.put("camunda.auth.persistence.type", storageType);
    propertyMap.put("camunda.auth.persistence.mode", "external");
    DefaultPropertiesPropertySource.addOrMerge(propertyMap, env.getPropertySources());
  }

  /**
   * Bridges legacy authentication-specific properties to the auth library's property namespace so
   * that auto-configuration beans activate correctly without manual bean wiring.
   *
   * <ul>
   *   <li>{@code camunda.security.authentication.unprotected-api} → {@code
   *       camunda.auth.unprotected-api}
   *   <li>{@code camunda.security.authentication.authenticationRefreshInterval} → {@code
   *       camunda.auth.session.refresh-interval}
   *   <li>Enables session holder when a webapp profile is active: {@code
   *       camunda.auth.session.enabled=true}
   * </ul>
   */
  private void bridgeAuthenticationProperties(final ConfigurableApplicationContext context) {
    final var env = context.getEnvironment();
    final var propertyMap = new HashMap<String, Object>();

    // Bridge unprotected-api flag
    final var oldUnprotected = env.getProperty("camunda.security.authentication.unprotected-api");
    if (oldUnprotected != null && env.getProperty("camunda.auth.unprotected-api") == null) {
      propertyMap.put("camunda.auth.unprotected-api", oldUnprotected);
    }

    // Bridge session refresh interval
    final var oldRefresh =
        env.getProperty("camunda.security.authentication.authenticationRefreshInterval");
    if (oldRefresh != null && env.getProperty("camunda.auth.session.refresh-interval") == null) {
      propertyMap.put("camunda.auth.session.refresh-interval", oldRefresh);
    }

    // Enable session holder when a webapp profile is active
    if (env.getProperty("camunda.auth.session.enabled") == null) {
      final Set<String> activeProfiles = Set.of(env.getActiveProfiles());
      if (webappProfileIsPresent(activeProfiles)) {
        propertyMap.put("camunda.auth.session.enabled", "true");
      }
    }

    if (!propertyMap.isEmpty()) {
      DefaultPropertiesPropertySource.addOrMerge(propertyMap, env.getPropertySources());
    }
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
