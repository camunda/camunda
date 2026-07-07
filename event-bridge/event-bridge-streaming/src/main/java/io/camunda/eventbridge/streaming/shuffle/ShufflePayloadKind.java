/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.shuffle;

/**
 * What a {@link ShuffleEnvelope}'s entries <em>are</em> (the "valueType" analog): drives which
 * downstream handler applies them. The hand-written facade counterpart of the generated wire enum —
 * consumers see only this type, and {@link ShuffleEnvelopeCodec} maps it onto the SBE encoding, so
 * the generated {@code shuffle.sbe} package stays internal to the codec.
 */
public enum ShufflePayloadKind {
  /** Entries are accumulator deltas of a sealed segment — non-idempotent, merged under dedup. */
  AGGREGATE_DELTA,
  /** Entries are reference records — idempotent by key, applied without segment dedup. */
  REFERENCE
}
