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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
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
  private static final String INDICATOR_NODE_ID_PROVIDER_READY = "nodeIdProviderReady";
  private static final String INDICATOR_GATEWAY_STARTED = "gatewayStarted";
  private static final String INDICATOR_BROKER_STARTUP = "brokerStartup";
  private static final String INDICATOR_OPERATE_INDICES_CHECK = "indicesCheck";
  private static final String INDICATOR_SPRING_READINESS_STATE = "readinessState";
  private static final String INDICATOR_TASKLIST_SEARCH_ENGINE_CHECK = "searchEngineCheck";

  private static final String SPRING_READINESS_PROPERTY =
      "management.health.readinessstate.enabled";
  private static final String SPRING_PROBES_PROPERTY = "management.endpoint.health.probes.enabled";
  private static final String SPRING_READINESS_GROUP_PROPERTY =
      "management.endpoint.health.group.readiness.include";
  private static final String HEALTH_GROUP_STATUS_PROPERTY =
      "management.endpoint.health.group.status.include";
  private static final String HEALTH_GROUP_LIVENESS_PROPERTY =
      "management.endpoint.health.group.liveness.include";

  @Value("camunda.mode")
  private String camundaMode;

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

    // --- Gateway Properties ---

    if (activeProfiles.contains(Profile.GATEWAY.getId())) {
      propertyMap.put("management.health.defaults.enabled", true);
      propertyMap.put(
          "management.endpoint.health.group.startup.include", INDICATOR_GATEWAY_STARTED);
      propertyMap.put("management.endpoint.health.group.startup.show-details", "never");

      final Set<String> livenessGroup = new HashSet<>();
      livenessGroup.add("livenessGatewayClusterAwareness");
      livenessGroup.add("livenessGatewayPartitionLeaderAwareness");
      livenessGroup.add("livenessMemory");
      propertyMap.put(HEALTH_GROUP_LIVENESS_PROPERTY, livenessGroup);
      propertyMap.put("management.endpoint.health.group.liveness.show-details", "always");

      propertyMap.put(
          "management.endpoint.health.status.order", "down,out-of-service,unknown,degraded,up");

      if (activeProfiles.contains(Profile.STANDALONE.getId())) {
        propertyMap.put(
            "camunda.security.multiTenancy.checksEnabled",
            "${zeebe.gateway.multiTenancy.enabled:false}");
      }
    }

    // --- Broker Properties ---

    if (activeProfiles.contains(Profile.BROKER.getId())) {
      propertyMap.put("management.endpoint.health.cache.time-to-live", "1s");
      propertyMap.put("management.endpoint.health.logging.slow-indicator-threshold", "10s");

      /* Configure broker status indicator */
      propertyMap.put(HEALTH_GROUP_STATUS_PROPERTY, "brokerStatus");
      propertyMap.put("management.endpoint.health.group.status.show-components", "never");
      propertyMap.put("management.endpoint.health.group.status.show-details", "never");

      /* Configure startup health check */
      propertyMap.put("management.endpoint.health.group.startup.show-components", "never");
      propertyMap.put("management.endpoint.health.group.startup.show-details", "never");
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
      healthIndicators.add(INDICATOR_NODE_ID_PROVIDER_READY);
    }

    if (activeProfiles.contains(Profile.GATEWAY.getId())) {
      healthIndicators.add(INDICATOR_GATEWAY_STARTED);
    }

    if (activeProfiles.contains(Profile.BROKER.getId())) {
      healthIndicators.add(INDICATOR_BROKER_STARTUP);
    }

    if (secondaryStorageEnabled && activeProfiles.contains(Profile.OPERATE.getId())) {
      healthIndicators.add(INDICATOR_SPRING_READINESS_STATE);
      if (DatabaseTypeUtils.isRdbmsDisabled(env)) {
        healthIndicators.add(INDICATOR_OPERATE_INDICES_CHECK);
      }
    }

    if (secondaryStorageEnabled
        && activeProfiles.contains(Profile.TASKLIST.getId())
        && DatabaseTypeUtils.isRdbmsDisabled(env)) {
      healthIndicators.add(INDICATOR_TASKLIST_SEARCH_ENGINE_CHECK);
    }

    if (secondaryStorageEnabled && activeProfiles.contains(Profile.IDENTITY.getId())) {
      healthIndicators.add(INDICATOR_SPRING_READINESS_STATE);
    }

    return healthIndicators;
  }
}
