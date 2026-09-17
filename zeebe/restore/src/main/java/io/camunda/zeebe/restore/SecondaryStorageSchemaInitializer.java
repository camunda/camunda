/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

/**
 * Applies one physical tenant's secondary-storage schema on demand, creating whatever indices are
 * missing and validating the ones that are there.
 *
 * <p>A restore into an already-restored secondary storage uses this to establish that the storage
 * carries every index the running version expects before any local partition data is dropped.
 *
 * <p>Implementations block and are idempotent: the same call may be repeated after a failure, and
 * may run concurrently with the same call on another broker restoring its own partitions.
 */
@FunctionalInterface
public interface SecondaryStorageSchemaInitializer {

  /** Applies the schema, throwing if it could not be applied. */
  void initialize();
}
