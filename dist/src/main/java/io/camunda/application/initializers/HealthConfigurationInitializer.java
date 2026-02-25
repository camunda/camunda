/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import io.camunda.application.Profile;
import io.camunda.application.commons.search.SchemaReadinessCheck;
import io.camunda.spring.utils.DatabaseTypeUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
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
  private static final String INDICATOR_SPRING_READINESS_STATE = "readinessState";
  private static final String BROKER_EMBEDDED_GATEWAY_ENABLED_PROPERTY =
      "zeebe.broker.gateway.enable";

  @Override
  public void initialize(final ConfigurableApplicationContext context) {
    final var environment = context.getEnvironment();
    final var propertySources = environment.getPropertySources();
    final var activeProfiles =
        Stream.of(environment.getActiveProfiles()).map(String::toLowerCase).toList();

    final var propertyMap = new HashMap<String, Object>();

    // Enables readinessState (Spring Boot's built-in ReadinessStateHealthIndicator)
    final var enableReadinessState = shouldReadinessState(activeProfiles);
    propertyMap.put("management.health.readinessstate.enabled", enableReadinessState);

    // Enables Kubernetes health groups endpoints (/actuator/health/{liveness,readiness,startup})
    final var enableProbes = shouldEnableProbes(activeProfiles);
    propertyMap.put("management.endpoint.health.probes.enabled", enableProbes);

    final var readinessGroupHealthIndicators =
        collectReadinessGroupHealthIndicators(activeProfiles, environment);
    if (!readinessGroupHealthIndicators.isEmpty()) {
      propertyMap.put(
          "management.endpoint.health.group.readiness.include", readinessGroupHealthIndicators);
    }

    final var livenessGroupHealthIndicators =
        collectLivenessGroupHealthIndicators(activeProfiles, environment);

    if (!livenessGroupHealthIndicators.isEmpty()) {
      propertyMap.put(
          "management.endpoint.health.group.liveness.include", livenessGroupHealthIndicators);
      propertyMap.put("management.endpoint.health.group.liveness.show-details", "always");
    }

    final Set<String> startupGroup = new HashSet<>();

    // --- Gateway Properties ---

    if (activeProfiles.contains(Profile.GATEWAY.getId())) {
      propertyMap.put("management.health.defaults.enabled", true);
      startupGroup.add(INDICATOR_GATEWAY_STARTED);
      propertyMap.put("management.endpoint.health.group.startup.show-details", "never");

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
      propertyMap.put("management.endpoint.health.group.status.include", "brokerStatus");
      propertyMap.put("management.endpoint.health.group.status.show-components", "never");
      propertyMap.put("management.endpoint.health.group.status.show-details", "never");

      /* Configure startup health check */
      startupGroup.add("brokerStartup");
      propertyMap.put("management.endpoint.health.group.startup.show-components", "never");
      propertyMap.put("management.endpoint.health.group.startup.show-details", "never");
    }

    if (!startupGroup.isEmpty()) {
      propertyMap.put("management.endpoint.health.group.startup.include", startupGroup);
    }

    // add or merges with the default property settings
    // that are added as last in the property sources
    DefaultPropertiesPropertySource.addOrMerge(propertyMap, propertySources);
  }

  protected boolean shouldEnableProbes(final List<String> activeProfiles) {
    return activeProfiles.stream()
        .anyMatch(
            Set.of(
                    Profile.OPERATE.getId(),
                    Profile.TASKLIST.getId(),
                    Profile.IDENTITY.getId(),
                    Profile.ADMIN.getId())
                ::contains);
  }

  protected boolean shouldReadinessState(final List<String> activeProfiles) {
    return activeProfiles.stream()
        .anyMatch(
            Set.of(
                    Profile.OPERATE.getId(),
                    Profile.TASKLIST.getId(),
                    Profile.BROKER.getId(),
                    Profile.IDENTITY.getId(),
                    Profile.ADMIN.getId())
                ::contains);
  }

  List<String> collectLivenessGroupHealthIndicators(
      final List<String> activeProfiles, final Environment env) {
    final var healthIndicators = new ArrayList<String>();

    if (activeProfiles.contains(Profile.BROKER.getId())) {
      healthIndicators.add(INDICATOR_BROKER_READY);
      healthIndicators.add(INDICATOR_NODE_ID_PROVIDER_READY);
    }

    if (activeProfiles.contains(Profile.GATEWAY.getId())) {
      healthIndicators.add("livenessGatewayClusterAwareness");
      healthIndicators.add("livenessGatewayPartitionLeaderAwareness");
      healthIndicators.add("livenessMemory");
    }

    return healthIndicators;
  }

  /** Returns a list of health indicators which will be member of the readiness group */
  List<String> collectReadinessGroupHealthIndicators(
      final List<String> activeProfiles, final Environment env) {
    final var healthIndicators = new ArrayList<String>();
    final boolean secondaryStorageEnabled = DatabaseTypeUtils.isSecondaryStorageEnabled(env);
    final boolean esOrOsEnabled = secondaryStorageEnabled && DatabaseTypeUtils.isRdbmsDisabled(env);
    final boolean hasWebappProfile =
        Profile.getWebappProfiles().stream().anyMatch(p -> activeProfiles.contains(p.getId()));

    if (activeProfiles.contains(Profile.BROKER.getId())) {
      healthIndicators.add(INDICATOR_BROKER_READY);
      healthIndicators.add(INDICATOR_NODE_ID_PROVIDER_READY);
    }

    if (activeProfiles.contains(Profile.GATEWAY.getId())) {
      healthIndicators.add(INDICATOR_GATEWAY_STARTED);
    }

    if (secondaryStorageEnabled && hasWebappProfile) {
      healthIndicators.add(INDICATOR_SPRING_READINESS_STATE);
    }

    // Schema readiness is only relevant for ES/OS and only when serving traffic
    // (broker with embedded gateway, standalone gateway, or webapp profiles).
    // It must not be added for other profiles like RESTORE.
    if (esOrOsEnabled) {
      if (hasWebappProfile
          || activeProfiles.contains(Profile.GATEWAY.getId())
          || (activeProfiles.contains(Profile.BROKER.getId())
              && env.getProperty(BROKER_EMBEDDED_GATEWAY_ENABLED_PROPERTY, Boolean.class, true))) {
        healthIndicators.add(SchemaReadinessCheck.SCHEMA_READINESS_CHECK);
      }
      // legacy ES/OS health indicators for Operate and Tasklist
      if (activeProfiles.contains(Profile.OPERATE.getId())) {
        healthIndicators.add("indicesCheck");
      }
      if (activeProfiles.contains(Profile.TASKLIST.getId())) {
        healthIndicators.add("searchEngineCheck");
      }
    }

    return healthIndicators;
  }
}
