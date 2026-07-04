/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.shuffle;

/**
 * One sealed cell delta with its origin coordinate — the record a windowed-aggregate operator
 * forwards downstream to a shuffle sink. It is the pre-transport unit: the {@link CellDelta}
 * carries the encoded cell (stream id, window, key, accumulator), and {@code (producerPartition,
 * segment)} is the origin the sink stamps onto the {@link ShuffleEnvelope} for deduplication.
 */
public record SegmentCell(int producerPartition, long segment, CellDelta cell) {}
