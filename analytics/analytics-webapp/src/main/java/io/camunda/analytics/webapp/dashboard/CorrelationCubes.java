/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.DimensionSpec;
import io.camunda.analytics.dimension.DimensionColumn;

/**
 * The naming-convention discovery for {@code corr-*} cubes: a catalog dataset named {@code corr-*}
 * is a correlation cube, and its dimension shape says which kind — shared by the duration, variant
 * and branch correlation reads so the convention lives in exactly one place.
 *
 * <ul>
 *   <li>exactly one {@code var.*} dimension + a {@code duration_p} percentile meter →{@link
 *       Kind#DURATION};
 *   <li>a {@code variantHash} dimension + exactly one {@code var.*} dimension → {@link
 *       Kind#VARIANT};
 *   <li>an {@code elementId} dimension + exactly one {@code var.*} dimension → {@link Kind#BRANCH}.
 * </ul>
 *
 * A cube matching none of these (no {@code corr-} prefix, no {@code var.*} dimension, or more than
 * one) is not classified — {@link #classify} returns {@code null} and callers skip it rather than
 * guessing.
 */
final class CorrelationCubes {

  private CorrelationCubes() {}

  /** The three shapes a {@code corr-*} cube can take. */
  enum Kind {
    DURATION,
    VARIANT,
    BRANCH
  }

  /** The kind of correlation cube {@code dataset} is, or {@code null} if it does not match one. */
  static Kind classify(final CompiledDataset dataset) {
    if (!dataset.name().startsWith("corr-")) {
      return null;
    }
    if (variableDimension(dataset) == null) {
      return null; // no var.* dimension, or more than one — not a recognized shape
    }
    if (hasDimension(dataset, "variantHash")) {
      return Kind.VARIANT;
    }
    if (hasDimension(dataset, "elementId")) {
      return Kind.BRANCH;
    }
    if (hasMeter(dataset, "duration_p")) {
      return Kind.DURATION;
    }
    return null;
  }

  /**
   * The single {@code var.*} dimension of {@code dataset}, or {@code null} when it declares none or
   * more than one (a correlation cube carries exactly one variable dimension by construction).
   */
  static String variableDimension(final CompiledDataset dataset) {
    String found = null;
    for (final DimensionColumn column : dataset.grain().columns()) {
      if (column.name().startsWith(DimensionSpec.VARIABLE_PREFIX)) {
        if (found != null) {
          return null;
        }
        found = column.name();
      }
    }
    return found;
  }

  private static boolean hasDimension(final CompiledDataset dataset, final String name) {
    return dataset.grain().indexOf(name) >= 0;
  }

  private static boolean hasMeter(final CompiledDataset dataset, final String meterName) {
    for (final CompiledMeter compiled : dataset.meters()) {
      if (compiled.meterName().equals(meterName)) {
        return true;
      }
    }
    return false;
  }
}
