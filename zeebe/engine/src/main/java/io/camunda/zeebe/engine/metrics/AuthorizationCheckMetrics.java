/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.AuthorizationCheckMetricsDoc.AUTHORIZATION_CHECK_TIME;
import static io.camunda.zeebe.engine.metrics.AuthorizationCheckMetricsDoc.GET_AUTHORIZED_RESORCE_IDENTIFIER_TIME;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class AuthorizationCheckMetrics {

  private final MeterRegistry meterRegistry;
  private final Map<ResourceTypePermissionType, ResourcePermissionAuthorizationCheckMetrics>
      metricsMap = new HashMap<>();

  public AuthorizationCheckMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void startAuthorizationCheck(
      final long key,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    getMetrics(resourceType, permissionType).startAuthorizationCheck(key);
  }

  public void stopAuthorizationCheck(
      final long key,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    getMetrics(resourceType, permissionType).stopAuthorizationCheck(key);
  }

  public void startGettingResourceIdentifiers(
      final long key,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    getMetrics(resourceType, permissionType).startGettingResourceIdentifiers(key);
  }

  public void stopGettingResourceIdentifiers(
      final long key,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    getMetrics(resourceType, permissionType).stopGettingResourceIdentifiers(key);
  }

  private ResourcePermissionAuthorizationCheckMetrics getMetrics(
      final AuthorizationResourceType resourceType, final PermissionType permissionType) {
    return metricsMap.computeIfAbsent(
        new ResourceTypePermissionType(resourceType, permissionType),
        k ->
            new ResourcePermissionAuthorizationCheckMetrics(
                meterRegistry, resourceType, permissionType));
  }

  private record ResourceTypePermissionType(
      AuthorizationResourceType resourceType, PermissionType permissionType) {}

  private static final class ResourcePermissionAuthorizationCheckMetrics {
    private final Map<Long, Long> pendingChecksMap = new HashMap<>();
    private final Map<Long, Long> getIdentifierMap = new HashMap<>();
    private final Timer authCheckTimer;
    private final Timer getResourceIdentifiersTimer;

    public ResourcePermissionAuthorizationCheckMetrics(
        final MeterRegistry meterRegistry,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType) {
      authCheckTimer =
          MicrometerUtil.buildTimer(AUTHORIZATION_CHECK_TIME)
              .tag("resourceType", resourceType.name())
              .tag("permissionType", permissionType.name())
              .register(meterRegistry);
      getResourceIdentifiersTimer =
          MicrometerUtil.buildTimer(GET_AUTHORIZED_RESORCE_IDENTIFIER_TIME)
              .tag("resourceType", resourceType.name())
              .tag("permissionType", permissionType.name())
              .register(meterRegistry);
    }

    public void startAuthorizationCheck(final long key) {
      pendingChecksMap.put(key, System.nanoTime());
    }

    public void stopAuthorizationCheck(final long key) {
      final long startTime = pendingChecksMap.remove(key);
      observeAuthorizationCheckTime(startTime, System.nanoTime());
    }

    private void observeAuthorizationCheckTime(final long startTime, final long endTime) {
      authCheckTimer.record(endTime - startTime, TimeUnit.NANOSECONDS);
    }

    public void startGettingResourceIdentifiers(final long key) {
      getIdentifierMap.put(key, System.nanoTime());
    }

    public void stopGettingResourceIdentifiers(final long key) {
      final long startTime = getIdentifierMap.remove(key);
      observeGetResourceIdentifiersTime(startTime, System.nanoTime());
    }

    private void observeGetResourceIdentifiersTime(final long startTime, final long endTime) {
      getResourceIdentifiersTimer.record(endTime - startTime, TimeUnit.NANOSECONDS);
    }
  }
}
