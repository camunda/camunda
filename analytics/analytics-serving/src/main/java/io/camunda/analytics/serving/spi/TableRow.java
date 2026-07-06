/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One raw row fetched from a {@link io.camunda.analytics.dataset.DatasetKind#TABLE table}: its
 * declared columns as an ordered {@code name -> value} map (a {@code null} value means the column
 * was absent for that row). The neutral unit the {@link DatasetQueryClient} returns for a table —
 * no windows, meters, or reduction, so it is served as fetched.
 */
public record TableRow(Map<String, Object> values) {

  public TableRow {
    // A column value may be null, so copy null-tolerantly (Map.copyOf rejects null values).
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
