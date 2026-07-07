/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the name-targeted enrichment read {@code variables(scopeKey, names)} (ADR 0008: the
 * projection-state interfaces move to byte views — {@code DirectBuffer} names/values — but must
 * preserve exactly these semantics): only the requested names are resolved, the nearest scope in
 * the parent chain wins, a name present nowhere is simply absent (never a null entry), the walk
 * ends at a scope with no element row, and the result agrees with the full-map read. The walk also
 * stops early once every requested name is resolved — not directly observable, but the
 * nearer-scope-wins assertions pin the semantics early termination must preserve.
 */
final class StateBackedProjectionStateNamedVariablesTest {

  private static final long PROCESS_SCOPE = 100L;
  private static final long SUBPROCESS_SCOPE = 200L;
  private static final long TASK_SCOPE = 300L;

  @Test
  void shouldResolveOnlyTheRequestedNames() throws Exception {
    // given a scope holding several variables
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.putVariable(PROCESS_SCOPE, "a", "1");
      state.putVariable(PROCESS_SCOPE, "b", "2");
      state.putVariable(PROCESS_SCOPE, "c", "3");

      // when only two names are requested
      final Map<String, String> resolved = state.variables(PROCESS_SCOPE, Set.of("a", "c"));

      // then exactly those are returned — no over-fetch of the scope's other variables
      assertThat(resolved).containsOnly(Map.entry("a", "1"), Map.entry("c", "3"));
    }
  }

  @Test
  void shouldLetTheNearerScopeWinOverTheParentChain() throws Exception {
    // given a three-level chain (task → subprocess → process) shadowing one name per level
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.activateElement(PROCESS_SCOPE, 1L, true, 0L);
      state.activateElement(SUBPROCESS_SCOPE, 2L, false, PROCESS_SCOPE);
      state.activateElement(TASK_SCOPE, 3L, false, SUBPROCESS_SCOPE);
      state.putVariable(PROCESS_SCOPE, "shadowed", "process");
      state.putVariable(PROCESS_SCOPE, "rootOnly", "root");
      state.putVariable(SUBPROCESS_SCOPE, "shadowed", "subprocess");
      state.putVariable(SUBPROCESS_SCOPE, "mid", "middle");
      state.putVariable(TASK_SCOPE, "shadowed", "task");

      // when resolving from the innermost scope
      final Map<String, String> resolved =
          state.variables(TASK_SCOPE, Set.of("shadowed", "mid", "rootOnly"));

      // then each name comes from the nearest scope that defines it
      assertThat(resolved)
          .containsOnly(
              Map.entry("shadowed", "task"),
              Map.entry("mid", "middle"),
              Map.entry("rootOnly", "root"));
    }
  }

  @Test
  void shouldOmitNamesThatExistNowhere() throws Exception {
    // given a chain that defines only one of the requested names
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.activateElement(PROCESS_SCOPE, 1L, true, 0L);
      state.activateElement(SUBPROCESS_SCOPE, 2L, false, PROCESS_SCOPE);
      state.putVariable(PROCESS_SCOPE, "present", "yes");

      // when a missing name is requested alongside a present one
      final Map<String, String> resolved =
          state.variables(SUBPROCESS_SCOPE, Set.of("present", "missing"));

      // then the missing name is absent — no null entry, no placeholder
      assertThat(resolved).containsOnly(Map.entry("present", "yes"));
      assertThat(resolved).doesNotContainKey("missing");
    }
  }

  @Test
  void shouldReturnEmptyForEmptyNames() throws Exception {
    // given a scope with variables
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.putVariable(PROCESS_SCOPE, "a", "1");

      // when no names are requested, then nothing is resolved (and nothing is read)
      assertThat(state.variables(PROCESS_SCOPE, Set.of())).isEmpty();
    }
  }

  @Test
  void shouldEndTheWalkAtAScopeWithoutAnElementRow() throws Exception {
    // given a variable on the process scope, but a child scope with no element row (its
    // activation was never folded / already evicted) — the parent pointer is unknowable
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.activateElement(PROCESS_SCOPE, 1L, true, 0L);
      state.putVariable(PROCESS_SCOPE, "a", "1");

      // when resolving from the row-less child scope
      final Map<String, String> resolved = state.variables(TASK_SCOPE, Set.of("a"));

      // then the chain cannot be walked past it — the parent's variable is NOT resolved
      assertThat(resolved).isEmpty();
    }
  }

  @Test
  void shouldResolveLocalVariablesOfARowLessScopeBeforeTheChainEnds() throws Exception {
    // given a scope with a local variable but no element row
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.putVariable(TASK_SCOPE, "local", "here");

      // when resolving from that scope, the local level is read before the chain walk stops
      assertThat(state.variables(TASK_SCOPE, Set.of("local")))
          .containsOnly(Map.entry("local", "here"));
    }
  }

  @Test
  void shouldAgreeWithTheFullMapReadForTheRequestedNames() throws Exception {
    // given a shadowing chain
    try (final StateBackedProjectionState state = StateBackedProjectionState.inMemory()) {
      state.activateElement(PROCESS_SCOPE, 1L, true, 0L);
      state.activateElement(SUBPROCESS_SCOPE, 2L, false, PROCESS_SCOPE);
      state.putVariable(PROCESS_SCOPE, "a", "root-a");
      state.putVariable(PROCESS_SCOPE, "b", "root-b");
      state.putVariable(SUBPROCESS_SCOPE, "a", "sub-a");

      // when reading via the name-targeted lookup and via the full prefix-scan snapshot
      final Map<String, String> named = state.variables(SUBPROCESS_SCOPE, Set.of("a", "b"));
      final Map<String, String> full = state.variables(SUBPROCESS_SCOPE);

      // then the two read paths agree on every requested name (same visibility rules)
      assertThat(named).containsOnly(Map.entry("a", "sub-a"), Map.entry("b", "root-b"));
      assertThat(full).containsAllEntriesOf(named);
    }
  }
}
