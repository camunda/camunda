/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl.changes;

import io.camunda.zeebe.dynamic.config.changes.PhysicalTenantConfigChangeExecutor;
import io.camunda.zeebe.dynamic.config.impl.PhysicalTenantConfigFetcher;
import io.camunda.zeebe.dynamic.config.impl.changes.appliers.ApplyPhysicalTenantConfigApplier;
import io.camunda.zeebe.dynamic.config.impl.changes.appliers.StopPhysicalTenantApplier;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ApplyPhysicalTenantConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.StopPhysicalTenantOperation;
import org.jspecify.annotations.NullMarked;

/**
 * Creates the appliers of a physical tenant's configuration changes. Unlike {@link
 * PartitionGroupConfigurationChangeAppliers}, these exist once per member rather than per group: a
 * tenant's module, which registers the group's appliers, may not run on the member yet.
 */
@NullMarked
public record PhysicalTenantConfigChangeAppliers(
    PhysicalTenantConfigFetcher fetcher, PhysicalTenantConfigChangeExecutor executor) {

  public PartitionGroupConfigurationChangeApplier applierFor(
      final String physicalTenantId, final ApplyPhysicalTenantConfigOperation operation) {
    return new ApplyPhysicalTenantConfigApplier(physicalTenantId, operation, fetcher, executor);
  }

  public PartitionGroupConfigurationChangeApplier applierFor(
      final String physicalTenantId, final StopPhysicalTenantOperation operation) {
    return new StopPhysicalTenantApplier(physicalTenantId, executor);
  }
}
