/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import java.util.List;

/**
 * The percentile control chart with its previous-period overlay: the {@code current} series over
 * the selected range and the {@code previous} series over the same-length range immediately before
 * it. The previous points are re-timestamped onto the current period's grid ({@code windowStart +
 * P}), so the client overlays the two series on one time axis without shifting anything itself.
 */
public record PercentileComparison(
    List<DurationPercentilePoint> current, List<DurationPercentilePoint> previous) {}
