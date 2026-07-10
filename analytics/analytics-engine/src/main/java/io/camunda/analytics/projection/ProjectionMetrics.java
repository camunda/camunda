/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

/**
 * The base projection's correctness signals. {@link #duplicateSkipped()} is expected traffic — it
 * makes exporter retries visible (the pre-fold watermark absorbing producer duplicates, ADR 0007).
 * The other two must stay at <b>zero</b> on a healthy pipeline: post-ADR-0007 a fold or derivation
 * can only meet a missing row if the routing/order invariant the watermark rests on broke (a Zeebe
 * partition's records split across source partitions, or out-of-order appends) — both were silent
 * no-ops before, i.e. invisible undercounting. The counters turn the fold's defensive null-guards
 * into alarm wires; the call sites log the record's coordinates for the forensic trail.
 */
public interface ProjectionMetrics {

  ProjectionMetrics NOOP =
      new ProjectionMetrics() {
        @Override
        public void duplicateSkipped() {}

        @Override
        public void foldRowMissing() {}

        @Override
        public void factDropped() {}
      };

  /**
   * A producer duplicate was skipped by the pre-fold watermark — expected under exporter retries.
   */
  void duplicateSkipped();

  /**
   * A fold met a missing row (no activation to finalize/flag) — must never happen; see class doc.
   */
  void foldRowMissing();

  /** A derivation dropped its fact because the row was missing — a silent undercount surfaced. */
  void factDropped();
}
