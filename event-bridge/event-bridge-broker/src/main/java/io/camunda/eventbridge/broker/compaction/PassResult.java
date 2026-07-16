/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

/**
 * The outcome of a single {@link CompactionPass#runOnce()}, for observability and for the
 * multi-pass driver to decide whether another pass is warranted.
 *
 * @param outcome what the pass did
 * @param cleanerPoint the cleaner point in effect after the pass (unchanged from before on {@link
 *     Outcome#NOTHING_TO_DO}/{@link Outcome#OVERFLOW_STALLED})
 * @param manifest the committed manifest, or {@code null} when nothing was committed
 * @param overflowed whether the key-offset map overflowed (a further pass is needed to finish the
 *     range even though this one committed progress)
 */
public record PassResult(
    Outcome outcome, long cleanerPoint, CompactionManifest manifest, boolean overflowed) {

  /** The kinds of pass outcome. */
  public enum Outcome {
    /** The target cleaner point did not advance past the previous one; nothing to do. */
    NOTHING_TO_DO,
    /** A new clean set was committed. */
    COMMITTED,
    /** The map overflowed before absorbing any new record; the pass could not make progress. */
    OVERFLOW_STALLED
  }

  static PassResult nothingToDo(final long cleanerPoint) {
    return new PassResult(Outcome.NOTHING_TO_DO, cleanerPoint, null, false);
  }

  static PassResult overflowStalled(final long cleanerPoint) {
    return new PassResult(Outcome.OVERFLOW_STALLED, cleanerPoint, null, false);
  }

  static PassResult committed(final CompactionManifest manifest, final boolean overflowed) {
    return new PassResult(Outcome.COMMITTED, manifest.cleanerPoint(), manifest, overflowed);
  }

  /** Returns {@code true} if this pass committed a new clean set. */
  public boolean committed() {
    return outcome == Outcome.COMMITTED;
  }
}
