/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Variable clearing, including the "has-variables" marker fast path: eviction of a scope that never
 * held a variable must be a no-op (it skips the prefix scan), and clearing a scope that did hold
 * variables must still remove them — even across a checkpoint, so the marker's durability matters.
 */
final class StateBackedProjectionStateVariablesTest {

  @Test
  void shouldClearVariablesForAScopeThatHasThem() throws Exception {
    // given
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.putVariable(100L, "a", "1");
      state.putVariable(100L, "b", "2");
      assertThat(state.variables(100L)).containsEntry("a", "1").containsEntry("b", "2");

      // when
      state.clearVariables(100L);

      // then
      assertThat(state.variables(100L)).isEmpty();
    }
  }

  @Test
  void shouldNoOpWhenClearingAScopeThatNeverHeldVariables() throws Exception {
    // given a scope with no variables (the common case the marker fast-paths)
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      // when / then — clearing is a harmless no-op, and an unrelated scope's variables are
      // untouched
      state.putVariable(200L, "keep", "yes");
      state.clearVariables(999L);

      assertThat(state.variables(999L)).isEmpty();
      assertThat(state.variables(200L)).containsEntry("keep", "yes");
    }
  }

  @Test
  void shouldStillClearVariablesAfterACheckpoint() throws Exception {
    // given variables and their scope marker made durable by a checkpoint
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.putVariable(300L, "x", "1");
      state.checkpoint();

      // when — the eviction path clears after the marker was flushed (not just held in the cache)
      state.clearVariables(300L);

      // then — the durable marker was seen, so the scan ran and removed the variable
      assertThat(state.variables(300L)).isEmpty();
    }
  }

  @Test
  void shouldReclearAfterVariablesAreSetAgainOnTheSameScope() throws Exception {
    // given a scope cleared once (marker removed), then reused
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.putVariable(400L, "a", "1");
      state.clearVariables(400L);
      assertThat(state.variables(400L)).isEmpty();

      // when a new variable is set on the same scope (re-marking it) and cleared again
      state.putVariable(400L, "b", "2");
      assertThat(state.variables(400L)).containsEntry("b", "2");
      state.clearVariables(400L);

      // then it is cleared again — the marker was re-established by the second put
      assertThat(state.variables(400L)).isEmpty();
    }
  }
}
