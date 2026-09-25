/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Where one physical tenant's schema initialization stands, as reported to an operator.
 *
 * @param state what the tenant's task is doing, or why it stopped
 * @param failedAttempts how many attempts have failed so far
 * @param lastFailure the most recent failure, or the one that stopped the task; null while none
 */
@NullMarked
public record SchemaInitializationStatus(
    State state, int failedAttempts, @Nullable Throwable lastFailure) {

  static final SchemaInitializationStatus INITIALIZING =
      new SchemaInitializationStatus(State.INITIALIZING, 0, null);
  static final SchemaInitializationStatus INITIALIZED =
      new SchemaInitializationStatus(State.INITIALIZED, 0, null);

  public enum State {
    /** No attempt has finished yet. */
    INITIALIZING,
    /** An attempt failed with a cause retrying may repair, and another one is scheduled. */
    RETRYING,
    /** Held back without an attempt while the tenant is in recovery mode; not a failure. */
    RECOVERING,
    /** The schema has been applied, so the tenant can be served. */
    INITIALIZED,
    /** An attempt failed with a cause retrying cannot repair, so no further one is made. */
    FAILED,
    /** Every configured attempt failed, so no further one is made. */
    GAVE_UP,
    /** The task could not be started, or ended outside any attempt. */
    ABORTED
  }
}
