/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The two-shaped {@code cohort} predicate {@code POST /api/tools/cohort-compare} (and {@code
 * .../exemplars}) accept, discriminated by their JSON {@code type} field.
 *
 * <p>Both variants split the entity's rows into a "slow" cohort (the predicate is true) and a
 * "fast" cohort (false) — that naming is literal for {@link Threshold} (typically a duration
 * threshold), and reused as-is for {@link WindowSplit} for one consistent response shape: there
 * {@code slow} means "on or after {@code at}" and {@code fast} means "before it", regardless of
 * whether the split actually correlates with speed.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CohortSpec.Threshold.class, name = "THRESHOLD"),
  @JsonSubTypes.Type(value = CohortSpec.WindowSplit.class, name = "WINDOW_SPLIT")
})
public sealed interface CohortSpec {

  /** {@code measure <op> value}, e.g. {@code duration_ms > 60000} — the "slow" predicate. */
  record Threshold(String measure, String op, double value) implements CohortSpec {}

  /** {@code ended_at >= at} — the "slow" (post-split) predicate. */
  record WindowSplit(String at) implements CohortSpec {}
}
