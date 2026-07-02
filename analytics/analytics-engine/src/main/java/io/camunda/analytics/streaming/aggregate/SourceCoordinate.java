/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

/**
 * Extracts a fact's source coordinate — the partition and monotonic position of the source record
 * it was derived from. Used as the idempotency key: a per-partition high-watermark drops any fact
 * at or below the last applied position, so replay/redelivery cannot fold the same fact twice.
 *
 * @param <F> the fact type
 */
public interface SourceCoordinate<F> {

  int partition(F fact);

  long position(F fact);
}
