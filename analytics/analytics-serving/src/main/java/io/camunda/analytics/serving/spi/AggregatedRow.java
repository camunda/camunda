/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import java.util.List;
import java.util.Map;

/**
 * One finalized output row of an {@link AggregatedFetch}: the {@code groupValues} (positional,
 * aligned to the fetch's group-by), the output time {@code bucket}, and the read-facing {@code
 * measures} per meter — already reduced and finalized by the store (recomposed additive columns, or
 * the sketch's denormalized {@code _value} for a {@code DIRECT} read). The {@link
 * DatasetQueryExecutor} unions these with the streamed sketch rows on {@code (groupValues,
 * bucket)}.
 */
public record AggregatedRow(List<Object> groupValues, long bucket, Map<String, Object> measures) {

  public AggregatedRow {
    groupValues = List.copyOf(groupValues);
    measures = Map.copyOf(measures);
  }
}
