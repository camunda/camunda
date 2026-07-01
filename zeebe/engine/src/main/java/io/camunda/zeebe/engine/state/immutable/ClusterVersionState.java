/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import java.util.Set;
import java.util.TreeSet;

/**
 * Engine Capability Version state. Stores the currently active (line, ordinal) the engine is
 * allowed to produce. A fresh cluster starts at the {@link Capability#BASELINE} ordinal — the
 * cluster is never at "ordinal 0"; even before any operator RAISE, the baseline is active and every
 * pre-ECV command and event is admitted under it.
 *
 * <p>The <em>ordinal</em> is the cluster's activation cursor — it's the value compared against
 * capability requirements. The <em>line</em> tracks which release the binary is on; it's exposed in
 * snapshots for operator visibility but does not participate in gate checks (ordinals are global
 * and backports carry them across lines, so the introduction line of a change is metadata, not a
 * requirement — deck slide 14).
 *
 * <p>Also stores the set of behavior flags that have been <em>explicitly suppressed</em> by an
 * operator. A suppressed flag has been activated by the cluster reaching its ordinal, but the
 * processor is told to take the legacy branch — a kill-switch overlay for "shipped a capability,
 * turned out wrong" scenarios. See {@code ClusterVersionFeatures}.
 */
public interface ClusterVersionState {

  int INITIAL_LINE = 0;

  /**
   * The ordinal a fresh cluster auto-activates on first startup — equals {@code
   * ClusterVersionCatalog.BASELINE_ORDINAL}. Inlined here to keep the {@code state.immutable}
   * package free of catalog imports; the architecture test in the catalog should assert they agree.
   */
  int INITIAL_ORDINAL = 1;

  int getActiveLine();

  int getActiveOrdinal();

  /** Is the named behavior flag currently suppressed (off regardless of ECV)? */
  boolean isSuppressed(String flagName);

  /** A read-only view of currently suppressed flag names — for operator inspection. */
  Set<String> getSuppressedFlags();

  /**
   * True when the engine has reached (or surpassed) the supplied ordinal. The active line is
   * <em>not</em> part of the check — ordinals are global and a change carries the same ordinal
   * everywhere it's present, so the cluster's current binary line doesn't gate the comparison.
   */
  default boolean isAtLeast(final int ordinal) {
    return getActiveOrdinal() >= ordinal;
  }

  /**
   * Snapshot of the engine-side cluster-version view at the moment of the call — what an operator
   * inspecting the broker via {@code /actuator/cluster-version} (or equivalent topology endpoint)
   * would see.
   *
   * <p>In multi-partition production setups, {@code confirmedLine} / {@code confirmedOrdinal} would
   * lag {@code activeLine} / {@code activeOrdinal} during the "briefly mixed" activation window the
   * deck names on slide 10 — partitions flip moments apart, and the cluster-scoped "confirmed" pair
   * updates only after every partition has acked. The single-partition PoC collapses both to the
   * same value.
   */
  default Snapshot getSnapshot() {
    final int line = getActiveLine();
    final int ordinal = getActiveOrdinal();
    return new Snapshot(line, ordinal, line, ordinal, new TreeSet<>(getSuppressedFlags()));
  }

  /**
   * Engine-side view of the cluster's current capability state.
   *
   * @param activeLine the release line of the currently running binary (display only)
   * @param activeOrdinal the highest activation ordinal — the value gates compare against
   * @param confirmedLine the line every partition has acknowledged (== active in single-partition)
   * @param confirmedOrdinal the ordinal every partition has acknowledged
   * @param suppressedFlags behavior flags an operator has explicitly disabled
   */
  record Snapshot(
      int activeLine,
      int activeOrdinal,
      int confirmedLine,
      int confirmedOrdinal,
      Set<String> suppressedFlags) {}
}
