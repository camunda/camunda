/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.shuffle;

/**
 * What the receiver should <em>do</em> with a {@link ShuffleEnvelope}'s entries (the "intent"
 * analog). {@link #MERGE} is non-idempotent and needs the segment dedup; {@link #UPSERT} and {@link
 * #DELETE} are idempotent by key and need none. The hand-written facade counterpart of the
 * generated wire enum — consumers see only this type, and {@link ShuffleEnvelopeCodec} maps it onto
 * the SBE encoding, so the generated {@code shuffle.sbe} package stays internal to the codec.
 */
public enum ShuffleOperation {
  MERGE,
  UPSERT,
  DELETE
}
