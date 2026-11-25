/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.tasks;

/**
 * Tells a background task whether it has to hold back its work, for example because exporting is
 * paused. A task asks before each unit of work, so implementations must return the current state on
 * every call and must be safe to call from any thread.
 */
@FunctionalInterface
public interface PauseStateSupplier {

  /** A supplier for tasks that never pause. */
  PauseStateSupplier NOT_PAUSED = () -> false;

  /**
   * @return true if the task must not do any work at the moment
   */
  boolean paused();
}
