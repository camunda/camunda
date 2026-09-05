/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import org.jspecify.annotations.NullMarked;

/**
 * How a node applies its physical tenants' secondary-storage schemas, in the two shapes that differ
 * in what one tenant's failure costs the others: {@link PerTenantSchemaInitialization} isolates it,
 * {@link SingleTenantSchemaInitialization} lets it abort startup.
 *
 * <p>Which shape a node takes is a property of the node, not of the attempt, so the
 * storage-specific caller supplies the attempt and reads readiness back through the same two
 * methods either way.
 */
@NullMarked
public interface SchemaInitialization extends AutoCloseable {

  /**
   * Applies the schemas, or starts the tasks that will.
   *
   * @throws Exception if the schema could not be applied and this shape does not isolate that
   */
  void start() throws Exception;

  /**
   * Blocks until this node can serve, for a shape that holds startup at all; returns at once for
   * one whose {@link #start()} has already done all the waiting there is.
   */
  void awaitGate();

  /**
   * Whether the physical tenant's schema has been applied. A shape that tracks the pass rather than
   * each tenant answers the same for every tenant, so screening out ids the node does not have is
   * the caller's, which is the side that holds them.
   */
  boolean isInitialized(String physicalTenantId);

  /** Stops any work still in flight; idempotent, and never throws. */
  @Override
  void close();
}
