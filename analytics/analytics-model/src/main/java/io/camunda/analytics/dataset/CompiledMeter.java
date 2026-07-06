/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.PushdownSpec;
import io.camunda.eventbridge.streaming.window.Windows;
import java.util.Optional;

/**
 * One meter of a cube materialised for one window tier: the runtime unit the two stages
 * instantiate. The {@code aggId} is stable per {@code (cube, meter, tier)} — its shuffle
 * routing/dispatch key — so different tiers of the same meter are independent rollups. {@code
 * bound} is the mergeable aggregate + accumulator codec; {@code windows} the tier's tumbling
 * windows; {@code meterName} the serving column it writes.
 */
public record CompiledMeter(
    String meterName, long windowMs, int aggId, BoundMeter<?, ?> bound, Windows windows) {

  /**
   * The meter's pushdown capability (carried from its {@link BoundMeter}): present when the store
   * can hold it as native numeric columns and aggregate them, empty for a blob (sketch / summary).
   */
  public Optional<PushdownSpec<?, ?>> pushdown() {
    return bound.pushdown().map(spec -> spec);
  }
}
