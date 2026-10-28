/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.application.commons.pt.LazyPhysicalTenantResource;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports a physical tenant's database through its pool, but only once this node uses the tenant:
 * checking a tenant the node does not use would start the very pool lazy initialization avoids. The
 * tenant's schema initialization is reported separately, and covers its database being reachable.
 */
@NullMarked
final class PhysicalTenantRdbmsHealthIndicator implements HealthIndicator {

  private final LazyPhysicalTenantResource<PhysicalTenantMyBatis> myBatis;
  private final HealthIndicator dataSourceHealth;

  PhysicalTenantRdbmsHealthIndicator(
      final LazyPhysicalTenantResource<PhysicalTenantMyBatis> myBatis,
      final HealthIndicator dataSourceHealth) {
    this.myBatis = myBatis;
    this.dataSourceHealth = dataSourceHealth;
  }

  @Override
  public @Nullable Health health() {
    return switch (myBatis.state()) {
      case CREATED -> dataSourceHealth.health();
      case NOT_CREATED -> Health.up().withDetail("state", "NOT_CREATED").build();
      case FAILED ->
          Health.down()
              .withDetail("state", "FAILED")
              .withDetail("error", Objects.toString(myBatis.lastFailure()))
              .build();
      case CLOSED -> Health.outOfService().withDetail("state", "CLOSED").build();
    };
  }
}
