/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

public interface OrdinalState {

  /**
   * Returns the current ordinal value. The ordinal is a monotonically incrementing counter
   * incremented once per minute. Returns {@code 0} if no ordinal has been set yet.
   */
  int getCurrentOrdinal();

  /**
   * Returns the epoch-millisecond wall-clock time at which the current ordinal was last assigned.
   * Returns {@code 0} if no ordinal has been set yet.
   */
  long getCurrentDateTime();
}
