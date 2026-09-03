/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.EngineConfiguration.OutputMappingMode;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMappings;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Utility methods for working with {@link MappingResolver} instances for output mappings. */
@NullMarked
public final class OutputMappingResolvers {

  private OutputMappingResolvers() {}

  /**
   * Returns a resolver for the given output mode. If {@code comparisonMode} is non-null and
   * different from {@code outputMode}, wraps both in a {@link ComparingMappingResolver} that logs a
   * warning when results differ.
   */
  public static MappingResolver<OutputMappings> forMode(
      final OutputMappingMode outputMode, final @Nullable OutputMappingMode comparisonMode) {
    final var primary = singleResolver(outputMode);
    if (comparisonMode == null || comparisonMode == outputMode) {
      return primary;
    }
    return new ComparingMappingResolver<>(primary, singleResolver(comparisonMode));
  }

  private static MappingResolver<OutputMappings> singleResolver(final OutputMappingMode mode) {
    return switch (mode) {
      case COMBINED -> new CombinedOutputMappingResolver();
      case ORDERED -> new OrderedOutputMappingResolver();
    };
  }
}
