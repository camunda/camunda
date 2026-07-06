/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import io.camunda.analytics.dimension.DimensionKey;
import java.util.Map;

/**
 * One raw stored cell as fetched from a backend: its grain-level {@link DimensionKey}, the tier
 * window it belongs to, and the still-encoded accumulator bytes per requested meter. The neutral
 * unit the {@link DatasetQueryClient} returns; the {@link DatasetQueryExecutor} decodes and merges
 * these in the application (uniform app-merge), so the backend need only filter and fetch.
 */
public record Cell(DimensionKey key, long windowStart, Map<String, byte[]> accumulators) {

  public Cell {
    accumulators = Map.copyOf(accumulators);
  }
}
