/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

/**
 * The accumulator for the execution-time metric: the intermediate state folded over per
 * window/group cell. {@code total} keeps the average exact under merge ({@code avg = total / count}
 * is derived only on read). An empty accumulator uses sentinel min/max so the first fact sets both.
 */
public record ExecutionTimeAccumulator(long count, long totalMs, long minMs, long maxMs) {

  public static ExecutionTimeAccumulator empty() {
    return new ExecutionTimeAccumulator(0L, 0L, Long.MAX_VALUE, Long.MIN_VALUE);
  }
}
