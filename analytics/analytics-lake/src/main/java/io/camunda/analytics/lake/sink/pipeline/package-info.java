/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * The L0 sink's control path: the per-(table x source partition) assembly ({@link
 * io.camunda.analytics.lake.sink.pipeline.SinkPipeline}) that owns the {@link
 * io.camunda.analytics.lake.sink.ColumnarSegmentRing} and the flush thread, and the trigger /
 * file-boundary / backpressure wiring around it ({@link
 * io.camunda.analytics.lake.sink.pipeline.FlushLoop}).
 *
 * <p>Nothing in this package implements the data path (vectors, sorting, encoding) — those are the
 * hot-path implementations built alongside this one. This package depends on them only through the
 * seams already frozen in {@code io.camunda.analytics.lake.sink}: an injected sorter function, a
 * {@link io.camunda.analytics.lake.sink.BatchEncoder.Factory}, and the {@link
 * io.camunda.analytics.lake.sink.SortedRun}/{@link io.camunda.analytics.lake.sink.Segment}
 * interfaces themselves.
 */
package io.camunda.analytics.lake.sink.pipeline;
