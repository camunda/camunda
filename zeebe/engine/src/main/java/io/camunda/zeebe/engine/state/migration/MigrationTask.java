/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

/**
 * Interface for migration tasks.
 *
 * <p>Implementations of this class can/must assume the following contract:
 *
 * <ul>
 *   <li>Implementation will be called before any processing is performed
 *   <li>Implementation will be called with an open database and open transaction
 *   <li>Implementation may be called more than once throughout the lifetime of the deployment of a
 *       given version of software in production
 *   <li>The method {@code runMigration(...)} will only be called after {@code needsToRun(...)} was
 *       called and did return {@code true}
 *   <li>All methods should be implemented with the context in mind, that they will be called
 *       synchronously during recovery.
 *   <li>Migrations that are expected to potentially take a long time, should only be implemented
 *       after https://github.com/camunda/camunda/issues/7248 has been solved
 *   <li>None of the methods must commit or roll back the transaction. The transaction is handled
 *       outside
 *   <li>Methods may throw exceptions to indicate a critical error during migration
 *       <ul>
 *         <li>Any exception thrown will cancel all subsequent migrations and will prevent the
 *             stream processor from starting
 *         <li>Therefore, great care shall be taken to handle exceptions and recoverable situations
 *             internally
 *       </ul>
 * </ul>
 */
public interface MigrationTask {

  /**
   * Returns identifier for the migration task.
   *
   * <p>The identifier is used for logging.
   *
   * <p>In the future, it might also be used to store the migrations that were run in persistent
   * state
   *
   * @return identifier for the migration task
   */
  String getIdentifier();

  /** Returns whether the migration needs to run. */
  boolean needsToRun(final MigrationTaskContext context);

  /**
   * Returns whether the migration is an initialization task, that must be executed even when the
   * database is empty.
   */
  default boolean isInitialization() {
    return false;
  }

  /** Implementations of this method perform the actual migration */
  void runMigration(final MutableMigrationTaskContext context);
}
