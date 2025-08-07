/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import io.camunda.application.Profile;
import io.camunda.spring.utils.DatabaseTypeUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.boot.DefaultPropertiesPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Collects and configures the readiness group depending on which applications/profiles are
 * activated.
 */
public class HealthConfigurationInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final String INDICATOR_BROKER_READY = "brokerReady";
  private static final String INDICATOR_GATEWAY_STARTED = "gatewayStarted";
  private static final String INDICATOR_OPERATE_INDICES_CHECK = "indicesCheck";
  private static final String INDICATOR_SPRING_READINESS_STATE = "readinessState";
  private static final String INDICATOR_TASKLIST_SEARCH_ENGINE_CHECK = "searchEngineCheck";

  private static final String SPRING_READINESS_PROPERTY =
      "management.health.readinessstate.enabled";
  private static final String SPRING_PROBES_PROPERTY = "management.endpoint.health.probes.enabled";
  private static final String SPRING_READINESS_GROUP_PROPERTY =
      "management.endpoint.health.group.readiness.include";

  @Override
  public void initialize(final ConfigurableApplicationContext context) {
    final var environment = context.getEnvironment();
    final var propertySources = environment.getPropertySources();
    final var activeProfiles =
        Stream.of(environment.getActiveProfiles()).map(String::toLowerCase).toList();

    final var healthIndicators = collectHealthIndicators(activeProfiles, environment);
    final var enableReadinessState = shouldReadinessState(activeProfiles);
    final var enableProbes = shouldEnableProbes(activeProfiles);

    final var propertyMap = new HashMap<String, Object>();

    // Enables readinessState
    propertyMap.put(SPRING_READINESS_PROPERTY, enableReadinessState);

    // Enables Kubernetes health groups
    propertyMap.put(SPRING_PROBES_PROPERTY, enableProbes);

    if (!healthIndicators.isEmpty()) {
      propertyMap.put(SPRING_READINESS_GROUP_PROPERTY, healthIndicators);
    }

    // add or merges with the default property settings
    // that are added as last in the property sources
    DefaultPropertiesPropertySource.addOrMerge(propertyMap, propertySources);
  }

  protected boolean shouldEnableProbes(final List<String> activeProfiles) {
    return activeProfiles.stream()
        .anyMatch(
            Set.of(Profile.OPERATE.getId(), Profile.TASKLIST.getId(), Profile.IDENTITY.getId())
                ::contains);
  }

  protected boolean shouldReadinessState(final List<String> activeProfiles) {
    return activeProfiles.stream()
        .anyMatch(
            Set.of(
                    Profile.OPERATE.getId(),
                    Profile.TASKLIST.getId(),
                    Profile.BROKER,
                    Profile.IDENTITY.getId())
                ::contains);
  }

  /** Returns a list of health indicators which will be member of the readiness group */
  protected List<String> collectHealthIndicators(
      final List<String> activeProfiles, final Environment env) {
    final var healthIndicators = new ArrayList<String>();
    final boolean secondaryStorageEnabled = DatabaseTypeUtils.isSecondaryStorageEnabled(env);

    if (activeProfiles.contains(Profile.BROKER.getId())) {
      healthIndicators.add(INDICATOR_BROKER_READY);
    }

    if (activeProfiles.contains(Profile.GATEWAY.getId())) {
      healthIndicators.add(INDICATOR_GATEWAY_STARTED);
    }

    if (secondaryStorageEnabled && activeProfiles.contains(Profile.OPERATE.getId())) {
      healthIndicators.add(INDICATOR_OPERATE_INDICES_CHECK);
      healthIndicators.add(INDICATOR_SPRING_READINESS_STATE);
    }

    if (secondaryStorageEnabled && activeProfiles.contains(Profile.TASKLIST.getId())) {
      healthIndicators.add(INDICATOR_TASKLIST_SEARCH_ENGINE_CHECK);
    }

    if (secondaryStorageEnabled && activeProfiles.contains(Profile.IDENTITY.getId())) {
      healthIndicators.add(INDICATOR_SPRING_READINESS_STATE);
    }

    return healthIndicators;
  }
}
