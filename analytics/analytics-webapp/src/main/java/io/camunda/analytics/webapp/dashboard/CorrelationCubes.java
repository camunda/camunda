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
import io.camunda.analytics.dimension.DimensionType;

/**
 * The naming-convention discovery for {@code corr-*} cubes: a catalog dataset named {@code corr-*}
 * is a correlation cube, and its dimension shape says which kind — shared by the duration, variant
 * and branch correlation reads so the convention lives in exactly one place. Every shape below also
 * validates the meters/dimensions the reads actually consume, not just the marker that picks the
 * shape: a classified-but-malformed cube would otherwise reach the planner with an undeclared
 * meter, which 400s the read forever and blanks the whole dashboard through the client's {@code
 * Promise.all}.
 *
 * <ul>
 *   <li>exactly one {@code var.*} dimension, a {@code count} meter and a {@code duration_p}
 *       percentile meter → {@link Kind#DURATION};
 *   <li>a {@code variantHash} dimension typed {@code LONG}, exactly one {@code var.*} dimension and
 *       a {@code count} meter → {@link Kind#VARIANT} (a {@code STRING}-typed {@code variantHash} is
 *       rejected — the read path's {@code asLong} would silently collapse it to {@code 0} instead
 *       of the real hash);
 *   <li>an {@code elementId} dimension, exactly one {@code var.*} dimension and a {@code count}
 *       meter → {@link Kind#BRANCH}.
 * </ul>
 *
 * A cube matching none of these (no {@code corr-} prefix, no {@code var.*} dimension or more than
 * one, a missing required meter, or a {@code variantHash} typed anything but {@code LONG}) is not
 * classified. A cube carrying <em>both</em> a {@code variantHash} and an {@code elementId} marker
 * is ambiguous — also not classified. {@link #classify} returns {@code null} in every such case and
 * callers skip the cube rather than guessing.
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
    final boolean hasVariantHash = hasDimension(dataset, "variantHash");
    final boolean hasElementId = hasDimension(dataset, "elementId");
    if (hasVariantHash && hasElementId) {
      return null; // both markers present — ambiguous, not a recognized shape
    }
    if (hasVariantHash) {
      // a STRING-typed variantHash would silently collapse to 0 via the read path's asLong — only
      // a LONG-typed one is trustworthy as the variant identity.
      return isLongDimension(dataset, "variantHash") && hasMeter(dataset, "count")
          ? Kind.VARIANT
          : null;
    }
    if (hasElementId) {
      return hasMeter(dataset, "count") ? Kind.BRANCH : null;
    }
    return hasMeter(dataset, "count") && hasMeter(dataset, "duration_p") ? Kind.DURATION : null;
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

  /** Whether {@code dataset} declares dimension {@code name} typed {@link DimensionType#LONG}. */
  private static boolean isLongDimension(final CompiledDataset dataset, final String name) {
    final int index = dataset.grain().indexOf(name);
    return index >= 0 && dataset.grain().column(index).type() == DimensionType.LONG;
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
