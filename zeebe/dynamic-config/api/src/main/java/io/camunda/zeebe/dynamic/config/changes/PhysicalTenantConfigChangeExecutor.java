/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import java.util.concurrent.CompletableFuture;

/**
 * Applies a change of a physical tenant's configuration on this member. Registered once per member,
 * since a tenant's module may not run on the member yet when the change arrives.
 *
 * <p>A failed future leaves the operation pending, and it is retried until it completes.
 */
public interface PhysicalTenantConfigChangeExecutor {

  /**
   * Runs the tenant with the given configuration: starts its module if it does not run on this
   * member, restarts it otherwise.
   *
   * @param physicalTenantId the tenant
   * @param reference the reference the configuration belongs to
   * @param configuration the configuration, already verified against the reference's hash
   */
  CompletableFuture<Void> apply(
      String physicalTenantId, PhysicalTenantConfigReference reference, byte[] configuration);

  /** Stops the tenant's module on this member, if it runs. Its data stays on disk. */
  CompletableFuture<Void> stop(String physicalTenantId);
}
