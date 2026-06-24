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

  @Override
  public void initialize(final ConfigurableApplicationContext context) {
    final var environment = context.getEnvironment();
    final var propertySources = environment.getPropertySources();
    final var activeProfiles =
        Stream.of(environment.getActiveProfiles()).map(String::toLowerCase).toList();

    final var propertyMap = new HashMap<String, Object>();

    // Enables Kubernetes health group endpoints (/actuator/health/{liveness,readiness,startup}).
    // Always enabled so that liveness and readiness endpoints are available for all profiles
    propertyMap.put("management.endpoint.health.probes.enabled", true);

    final var readinessGroupHealthIndicators =
        collectReadinessGroupHealthIndicators(activeProfiles, environment);
    if (!readinessGroupHealthIndicators.isEmpty()) {
      propertyMap.put(
          "management.endpoint.health.group.readiness.include", readinessGroupHealthIndicators);
    }

    final var livenessGroupHealthIndicators = collectLivenessGroupHealthIndicators(activeProfiles);
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

  /**
   * Returns health indicators for the liveness group. Liveness must never depend on external
   * systems (like Elasticsearch/OpenSearch) to avoid cascading pod restarts when the search engine
   * is temporarily unavailable.
   *
   * <p>For the broker profile, {@code brokerReady} and {@code nodeIdProviderReady} are included so
   * that a broker stuck in an unready state (e.g., partitions never forming) is eventually
   * restarted by Kubernetes.
   *
   * <p>For the gateway profile, delayed liveness indicators with built-in grace periods are used to
   * avoid restarting on transient issues.
   */
  List<String> collectLivenessGroupHealthIndicators(final List<String> activeProfiles) {
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
  protected List<String> collectReadinessGroupHealthIndicators(
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

    if (secondaryStorageEnabled) {
      final boolean isWebappProfile =
          Profile.getWebappProfiles().stream().anyMatch(p -> activeProfiles.contains(p.getId()));
      if (isWebappProfile) {
        healthIndicators.add(INDICATOR_SPRING_READINESS_STATE);
      }
      if (DatabaseTypeUtils.isRdbmsDisabled(env)) {
        if (isAnyHttpGatewayEnabled(env)
            && (isWebappProfile
                || activeProfiles.contains(Profile.GATEWAY.getId())
                || activeProfiles.contains(Profile.BROKER.getId()))) {
          healthIndicators.add(SchemaReadinessCheck.SCHEMA_READINESS_CHECK);
        }
        if (activeProfiles.contains(Profile.OPERATE.getId())) {
          healthIndicators.add("indicesCheck");
        }
        if (activeProfiles.contains(Profile.TASKLIST.getId())) {
          healthIndicators.add("searchEngineCheck");
        }
      }
    }

    return healthIndicators;
  }

  /**
   * Same condition as {@link
   * io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled}
   */
  private boolean isAnyHttpGatewayEnabled(final Environment env) {
    return env.getProperty("zeebe.broker.gateway.enable", Boolean.class, true)
        && (env.getProperty("camunda.rest.enabled", Boolean.class, true)
            || env.getProperty("camunda.mcp.enabled", Boolean.class, false));
  }
}
