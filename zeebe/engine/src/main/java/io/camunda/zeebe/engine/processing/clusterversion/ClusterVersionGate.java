/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import io.camunda.zeebe.engine.state.immutable.ClusterVersionState;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.OptionalInt;

/**
 * Command-admission gate, consulted by the broker's command-API layer <em>before</em> a command is
 * written to the log. When the gate rejects, the broker responds synchronously and never calls
 * {@code logStreamWriter.tryWrite} — so no record reaches the log stream.
 *
 * <p>The registry of which commands are gated lives in {@link ClusterVersionCatalog}. The check
 * compares the cluster's active ordinal against the capability's required ordinal — the active line
 * is not part of the comparison (ordinals are global; backports carry the same ordinal across
 * lines, so the line a capability was introduced on is metadata, not a requirement).
 */
public final class ClusterVersionGate {

  private final ClusterVersionState state;

  public ClusterVersionGate(final ClusterVersionState state) {
    this.state = state;
  }

  /** Ordinal gating a (ValueType, Intent) at the admission layer, or empty if not gated. */
  public static OptionalInt requirementFor(final ValueType valueType, final Intent intent) {
    return ClusterVersionCatalog.requiredOrdinalForCommand(valueType, intent);
  }

  /** Intent-only lookup — used by the engine command writer. */
  public static OptionalInt requirementFor(final Intent intent) {
    return ClusterVersionCatalog.requiredOrdinalForCommand(intent);
  }

  /** Broker admission path. ValueType stays for context but the lookup keys on intent alone. */
  public static boolean admits(
      final ValueType valueType, final Intent intent, final int activeOrdinal) {
    return admits(intent, activeOrdinal);
  }

  /** Intent-only variant — used by the engine command writer. */
  public static boolean admits(final Intent intent, final int activeOrdinal) {
    final int required = ClusterVersionCatalog.requiredOrdinalForCommandOrUngated(intent);
    return required < 0 || activeOrdinal >= required;
  }

  /** Instance check used by engine code with direct access to {@link ClusterVersionState}. */
  public boolean isAvailable(final ValueType valueType, final Intent intent) {
    return admits(valueType, intent, state.getActiveOrdinal());
  }
}
