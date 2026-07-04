/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.shuffle;

/**
 * One cell's contribution within a {@link ShuffleEnvelope} batch: an encoded accumulator delta (or
 * reference record) for the stream {@code streamId}, grouping {@code key}, and event-time {@code
 * windowStart}. The reducer merges it into the cell {@code (streamId, key, windowStart)}. Key and
 * payload are opaque bytes — the substrate carries them; the application supplies the codecs.
 */
public record CellDelta(int streamId, long windowStart, byte[] key, byte[] payload) {}
