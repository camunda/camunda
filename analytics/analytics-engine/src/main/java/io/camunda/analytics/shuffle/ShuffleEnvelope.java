/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import io.camunda.analytics.shuffle.sbe.Operation;
import io.camunda.analytics.shuffle.sbe.PayloadKind;

/**
 * The logical view of one shuffled record, encoded on the wire by {@link ShuffleEnvelopeCodec} as
 * the SBE {@code ShuffleEnvelope} message (schema id 300). The SBE {@code messageHeader} carries
 * the wire version, so the format evolves natively — this record is just the decoded snapshot.
 *
 * <p>The header fields let Stage 2 dispatch without decoding the payload, mirroring how Zeebe's
 * {@code RecordMetadata} carries {@code valueType} + {@code intent}: {@link #payloadKind} says what
 * the payload is (an aggregate delta vs a reference record) and {@link #operation} says what to do
 * with it (merge / upsert / delete). {@link #aggId}/{@link #key} route and identify the target,
 * {@link #windowStart} is the event-time window, and {@link #producerPartition}/{@link #segment}
 * are the dedup coordinate Stage 2 tracks as a per-partition watermark. {@link #schemaVersion} is
 * the payload/dataset schema version (distinct from the SBE wire version); {@link #producedAt} is
 * wall-clock observability only and never affects correctness.
 */
public record ShuffleEnvelope(
    long producedAt,
    int schemaVersion,
    int aggId,
    byte[] key,
    long windowStart,
    int producerPartition,
    long segment,
    PayloadKind payloadKind,
    Operation operation,
    byte[] payload) {}
