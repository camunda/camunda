/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.dimension.DimensionSchema;
import java.util.List;

/**
 * The physical serving schema of a cube, derived from a {@link DatasetDeclaration}: the {@link
 * DimensionSchema grain} columns plus one accumulator column per meter, with an implicit {@code
 * window_start} and {@code window_size} (the tier). The primary key is {@code (dimensions,
 * window_start, window_size)} — one cell per grain value per window per tier. A backend's schema
 * manager turns this into a table (JDBC now) or an index+mapping (ES/OS, Phase 4); the meter
 * columns hold the merged accumulator so a read decodes and finalizes it.
 */
public record DatasetSchema(DimensionSchema dimensions, List<String> meterNames) {

  public DatasetSchema {
    meterNames = List.copyOf(meterNames);
  }
}
