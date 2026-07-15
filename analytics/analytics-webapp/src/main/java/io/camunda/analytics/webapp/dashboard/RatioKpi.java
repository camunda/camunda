/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One ratio meter aggregated to a single number over a period (the whole-range total row, not a
 * per-window series): {@code matched} of {@code total} facts satisfied the ratio's predicate. A
 * zero {@code total} means the period had no facts in the ratio's population — the ratio is 0 by
 * convention and consumers should treat the KPI as absent.
 */
public record RatioKpi(long matched, long total, double ratio) {

  /** The empty period: no facts, ratio 0 — consumers render a dash rather than "0%". */
  public static final RatioKpi EMPTY = new RatioKpi(0L, 0L, 0.0);
}
