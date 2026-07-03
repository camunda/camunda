/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

/**
 * One cell's contribution within a {@link ShuffleEnvelope} batch: an encoded accumulator delta (or
 * reference record) for the aggregation {@code aggId}, grouping {@code key}, and event-time {@code
 * windowStart}. The reducer merges it into the cell {@code (aggId, key, windowStart)}.
 */
public record CellDelta(int aggId, long windowStart, byte[] key, byte[] payload) {}
