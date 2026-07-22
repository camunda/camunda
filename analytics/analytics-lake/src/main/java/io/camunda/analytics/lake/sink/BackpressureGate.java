/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * How ring geometry reaches the record source: {@link #pause()} must stop record consumption
 * (event-bridge consumer pause — unread records wait durably on the broker; overload becomes
 * consumer lag, never memory growth), {@link #resume()} re-enables it.
 *
 * <p>Called from ring code only: {@code pause()} on the poll thread (inside a failed seal), {@code
 * resume()} on the flush thread (inside a release) — implementations must be safe for that pair and
 * idempotent in both directions.
 */
public interface BackpressureGate {

  void pause();

  void resume();
}
