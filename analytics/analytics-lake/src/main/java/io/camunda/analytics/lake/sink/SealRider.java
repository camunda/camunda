/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * Reserved seam for fold-at-flush riders: row-local gold measures (counts, sums, histogram-bin
 * partials) computed from the sorted run the flush thread already produced — the data's one
 * guaranteed moment in RAM. Instance-assembled measures (variants, KPIs) must NOT ride here; they
 * belong to the completion-triggered lane (see the maintenance-plane design).
 *
 * <p>Currently unimplemented by design: the interface exists so the pipeline can offer the hook
 * without the riders' machinery existing yet.
 */
public interface SealRider {

  /** Called once per sealed segment, on the flush thread, after sorting and before encoding. */
  void onSealed(SortedRun run);
}
