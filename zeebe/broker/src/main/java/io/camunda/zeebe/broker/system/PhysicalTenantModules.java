/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Starts and stops the broker module of one physical tenant while the broker runs: its job stream
 * service, its partition manager and its admin service. The modules of the other tenants keep
 * running, and the readiness of the node does not depend on a module started here until its
 * partitions are installed.
 *
 * <p>Calls are applied one at a time on the broker startup actor.
 */
@NullMarked
public interface PhysicalTenantModules {

  /**
   * Starts the module of a tenant that does not run on this broker. If the cluster configuration
   * has no partition group for the tenant yet, this broker provisions it when it is the
   * coordinator.
   *
   * @return completes once the module runs; its partitions install afterwards, see {@link
   *     #awaitReady(String)}
   */
  CompletableFuture<Void> start(String physicalTenantId, PhysicalTenantContext context);

  /**
   * Stops the module of a running tenant and forgets its context. The data of the tenant stays on
   * disk.
   */
  CompletableFuture<Void> stop(String physicalTenantId);

  /**
   * @return completes once every local partition of the tenant is installed; from then on the
   *     tenant counts towards the readiness of the node
   */
  CompletableFuture<Void> awaitReady(String physicalTenantId);

  /** The tenants whose module currently runs on this broker. */
  Set<String> running();

  /** The context the running module of a tenant was started with. */
  Optional<PhysicalTenantContext> context(String physicalTenantId);
}
