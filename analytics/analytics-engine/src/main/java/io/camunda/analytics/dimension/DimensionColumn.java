/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import java.util.Objects;

/** One named, typed column of a {@link DimensionSchema} (e.g. {@code region:STRING}). */
public record DimensionColumn(String name, DimensionType type) {

  public DimensionColumn {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    if (name.isBlank()) {
      throw new IllegalArgumentException("dimension column name must not be blank");
    }
  }
}
