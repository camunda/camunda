/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * Extracts a value's source coordinate — the partition and monotonic position of the source record
 * it was derived from. Used as the idempotency key: a per-partition high-watermark drops any value
 * at or below the last applied position, so replay/redelivery cannot fold the same value twice.
 *
 * @param <F> the value type
 */
public interface SourceCoordinate<F> {

  int partition(F value);

  long position(F value);
}
