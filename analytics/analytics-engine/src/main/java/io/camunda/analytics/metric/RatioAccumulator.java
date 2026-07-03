/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

/**
 * The accumulator for the ratio metric: {@code matched} facts (those satisfying the numerator
 * predicate) out of {@code total} folded. Both are additive, so {@code merge} is component-wise
 * addition — commutative and associative — and the ratio {@code matched / total} is derived only on
 * read. Expresses part-of-whole cohorts (e.g. SLA-compliant share, no-incident share).
 */
public record RatioAccumulator(long matched, long total) {

  public static RatioAccumulator empty() {
    return new RatioAccumulator(0L, 0L);
  }
}
