/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * The L0 sink: turns the translator's row stream into sorted Parquet files registered with Iceberg,
 * at a fixed memory footprint, with zero steady-state allocation on the hot path.
 *
 * <h2>Threading model (the load-bearing contract)</h2>
 *
 * <p>Per (source partition x table) there are exactly two threads:
 *
 * <ul>
 *   <li><b>The poll thread (hot)</b> appends rows into the currently-filling {@link
 *       io.camunda.analytics.lake.sink.Segment} of a {@link
 *       io.camunda.analytics.lake.sink.ColumnarSegmentRing} via a {@link
 *       io.camunda.analytics.lake.sink.RowAppender}. It never blocks, never performs IO, and never
 *       allocates at steady state. It alone advances the ring's head.
 *   <li><b>The flush thread (cold)</b> takes sealed segments from the ring, sorts them (permutation
 *       + gather), routes rows by family day, and streams them through a {@link
 *       io.camunda.analytics.lake.sink.BatchEncoder} into a {@link
 *       io.camunda.analytics.lake.sink.FileSink}. Its allocations are quarantined and budgeted
 *       (per-flush, not per-record). It alone advances the ring's tail.
 * </ul>
 *
 * <p>Overload converts into consumer lag, never memory growth: when the ring has no free segment,
 * the poll thread pauses consumption through the {@link
 * io.camunda.analytics.lake.sink.BackpressureGate} until the flush thread releases a slot.
 *
 * <p>"Never allocates at steady state" above is the per-<b>record</b> path's contract, not an
 * absolute one: {@code io.camunda.analytics.lake.translate.LakeTranslator}'s own work upstream of a
 * {@link io.camunda.analytics.lake.sink.RowAppender} call — building the {@code vars_json} payload
 * and draining {@code io.camunda.analytics.lake.state.RocksDbTranslatorState#variablesOf} — does
 * allocate, once per completed instance. That cost is bounded by how often an instance finishes,
 * never by how many records it took to get there, and is the one explicitly budgeted exception; see
 * {@link io.camunda.analytics.lake.sink.RowAppender}'s own javadoc.
 *
 * <h2>Data path vs control path</h2>
 *
 * <p>Data path: rows &rarr; segment vectors &rarr; sorted run &rarr; Parquet bytes &rarr; object
 * store. Control path: seal triggers, {@link io.camunda.analytics.lake.sink.Descriptor}s carrying
 * (file, offset range, frontier) to the {@link io.camunda.analytics.lake.sink.DescriptorSink}, and
 * backpressure signals. Flush timing is a pure performance knob: flushing at any moment must yield
 * an identical committed table state.
 */
package io.camunda.analytics.lake.sink;
