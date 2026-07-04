/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.dispatch;

import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.projection.applier.EventApplier;
import io.camunda.analytics.projection.derive.FactDeriver;

/**
 * One {@code (ValueType, Intent)} transition's handler: the ordered Model-A steps — {@code apply}
 * (fold the record into the row) → {@code derive} (read the updated row, forward facts) → {@code
 * evict} (drop the terminal row). Any step may be absent (a variable set only applies; a process
 * deployment only derives; a completion does all three). Both {@code apply} and {@code evict} are
 * {@link EventApplier}s, so mutation stays the appliers' sole responsibility.
 */
public final class RecordHandler {

  private final EventApplier apply;
  private final FactDeriver derive;
  private final EventApplier evict;

  private RecordHandler(
      final EventApplier apply, final FactDeriver derive, final EventApplier evict) {
    this.apply = apply;
    this.derive = derive;
    this.evict = evict;
  }

  /** Applies only (no fact, no eviction) — e.g. a variable set. */
  public static RecordHandler apply(final EventApplier apply) {
    return new RecordHandler(apply, null, null);
  }

  /** Derives only (no state) — e.g. a process-definition deployment. */
  public static RecordHandler derive(final FactDeriver derive) {
    return new RecordHandler(null, derive, null);
  }

  /** Applies then derives — e.g. an activation. */
  public static RecordHandler applyDerive(final EventApplier apply, final FactDeriver derive) {
    return new RecordHandler(apply, derive, null);
  }

  /** Applies, derives, then evicts the terminal row — e.g. a completion. */
  public static RecordHandler applyDeriveEvict(
      final EventApplier apply, final FactDeriver derive, final EventApplier evict) {
    return new RecordHandler(apply, derive, evict);
  }

  void handle(final SourceRecord source) {
    if (apply != null) {
      apply.apply(source);
    }
    if (derive != null) {
      derive.derive(source);
    }
    if (evict != null) {
      evict.apply(source);
    }
  }
}
