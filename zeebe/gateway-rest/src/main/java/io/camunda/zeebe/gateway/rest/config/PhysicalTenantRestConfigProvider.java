/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import java.time.Duration;

/** Resolves the REST-layer configuration view for a given physical tenant. */
public interface PhysicalTenantRestConfigProvider {

  TenantRestConfig forTenant(String physicalTenantId);

  record TenantRestConfig(int maxNameFieldLength, JobMetrics jobMetrics) {}

  record JobMetrics(
      boolean enabled,
      Duration exportInterval,
      int maxWorkerNameLength,
      int maxJobTypeLength,
      int maxTenantIdLength,
      int maxUniqueKeys) {

    public static final JobMetrics DEFAULT =
        new JobMetrics(true, Duration.ofMinutes(5), 100, 100, 30, 9500);
  }
}
