/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ClusterVersionState;

public interface MutableClusterVersionState extends ClusterVersionState {

  /** Activate (line, ordinal) as the new ECV. Idempotent — re-applying the same pair is a no-op. */
  void activate(int line, int ordinal);

  /** Add the named flag to the suppressed set. Idempotent. */
  void suppressFlag(String flagName);

  /** Remove the named flag from the suppressed set. Idempotent. */
  void unsuppressFlag(String flagName);
}
