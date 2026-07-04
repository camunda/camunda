/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.shuffle;

import io.camunda.eventbridge.streaming.shuffle.sbe.Operation;
import io.camunda.eventbridge.streaming.shuffle.sbe.PayloadKind;
import java.util.List;

/**
 * The logical view of one shuffled record, encoded on the wire by {@link ShuffleEnvelopeCodec} as
 * the SBE {@code ShuffleEnvelope} message (schema id 300). It is a <em>batch</em> of a sealed
 * segment's {@link CellDelta}s that route to one downstream partition, so {@code (producerPartition,
 * segment, chunk)} is a single atomic dedup unit — the reducer applies the whole batch or none, and
 * a re-emit is skipped wholesale.
 *
 * <p>The header lets the reducer dispatch without decoding the payload: {@link #payloadKind} says
 * what the entries are (aggregate deltas vs reference records) and {@link #operation} says what to
 * do (merge / upsert / delete). Only {@code AGGREGATE_DELTA}/{@code MERGE} needs the segment dedup;
 * {@code UPSERT}/{@code DELETE} are idempotent by key. An oversized segment is split into ordered
 * chunks; {@link #moreChunks} flags that a further chunk of the same {@code (producerPartition,
 * segment)} follows. {@link #schemaVersion} is the payload schema version (distinct from the SBE
 * wire version); {@link #producedAt} is wall-clock observability only and never affects correctness.
 */
public record ShuffleEnvelope(
    long producedAt,
    int schemaVersion,
    int producerPartition,
    long segment,
    int chunk,
    boolean moreChunks,
    PayloadKind payloadKind,
    Operation operation,
    List<CellDelta> cells) {

  public ShuffleEnvelope {
    cells = List.copyOf(cells);
  }
}
