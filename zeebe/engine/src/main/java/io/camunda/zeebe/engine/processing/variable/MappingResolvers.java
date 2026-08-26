/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.EngineConfiguration.InputMappingMode;
import org.jspecify.annotations.Nullable;

/** Utility methods for working with {@link MappingResolver} instances. */
public final class MappingResolvers {

  private MappingResolvers() {}

  /**
   * Returns a resolver for the given input mode. If {@code comparisonMode} is non-null and
   * different from {@code inputMode}, wraps both in a {@link ComparingMappingResolver} that logs a
   * warning when results differ.
   */
  public static MappingResolver forMode(
      final InputMappingMode inputMode, final @Nullable InputMappingMode comparisonMode) {
    final var primary = singleResolver(inputMode);
    if (comparisonMode == null || comparisonMode == inputMode) {
      return primary;
    }
    return new ComparingMappingResolver(primary, singleResolver(comparisonMode));
  }

  private static MappingResolver singleResolver(final InputMappingMode mode) {
    return switch (mode) {
      case COMBINED -> new CombinedMappingResolver();
      case ORDERED -> new OrderedMappingResolver();
    };
  }
}
