/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import io.camunda.analytics.dimension.FactRow;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;

/**
 * A {@link Meter} resolved against its {@link MeterType}: the mergeable {@link AggregateFunction}
 * that folds facts (read through the {@link FactRow} seam) into its accumulator, paired with the
 * {@link RecordValue} codec that serializes that accumulator for the durable rollup and the
 * shuffle. This is what the pipeline stages consume — one bound meter per declared metric.
 *
 * @param <ACC> the accumulator type
 * @param <OUT> the read-facing result type
 */
public record BoundMeter<ACC, OUT>(
    Meter meter,
    AggregateFunction<FactRow, ACC, OUT> aggregate,
    RecordValue<ACC> accumulatorCodec) {}
