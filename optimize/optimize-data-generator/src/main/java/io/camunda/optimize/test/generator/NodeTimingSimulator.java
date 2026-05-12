/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.zeebe.protocol.record.value.BpmnElementType.END_EVENT;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.EVENT_BASED_GATEWAY;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.EXCLUSIVE_GATEWAY;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.START_EVENT;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.SUB_PROCESS;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.List;
import java.util.Random;

/**
 * Distributes a process-instance time window across its flow nodes, producing per-node timestamps
 * that drive realistic heatmap data in Optimize.
 *
 * <p>Events and gateways are treated as near-instant (weight 0.01). Work nodes receive random
 * weights in {@code [0.5, 1.5)}; ~15 % are promoted to a high-outlier weight (3–5×) so the Optimize
 * heatmap highlights them as higher-duration nodes.
 *
 * <p>This class owns only the timing algorithm. How those timestamps are embedded in Zeebe records
 * is the responsibility of {@link ZeebeRecordFactory}.
 */
class NodeTimingSimulator {

  private final Random rng;

  NodeTimingSimulator(final Random rng) {
    this.rng = rng;
  }

  /**
   * Returns {@code n + 1} monotonically increasing timestamps where {@code result[i]} is the start
   * of node {@code i} and {@code result[i+1]} is its end, distributing {@code startMs..endMs}
   * proportionally by node weight.
   */
  long[] compute(final List<FlowNode> nodes, final long startMs, final long endMs) {
    final int n = nodes.size();
    final long totalMs = Math.max(1L, endMs - startMs);
    final double[] weights = new double[n];
    double sum = 0;

    for (int i = 0; i < n; i++) {
      weights[i] = isInstant(nodes.get(i).type()) ? 0.01 : workNodeWeight();
      sum += weights[i];
    }

    final long[] timestamps = new long[n + 1];
    timestamps[0] = startMs;
    long cursor = startMs;
    for (int i = 0; i < n - 1; i++) {
      cursor += (long) (totalMs * weights[i] / sum);
      timestamps[i + 1] = cursor;
    }
    timestamps[n] = endMs;
    return timestamps;
  }

  private static boolean isInstant(final BpmnElementType type) {
    // Sub-process elements have no duration of their own — their time span is derived from
    // children.
    // Event-based gateways are routing elements and do not hold time themselves.
    return type == START_EVENT
        || type == END_EVENT
        || type == EXCLUSIVE_GATEWAY
        || type == EVENT_BASED_GATEWAY
        || type == SUB_PROCESS;
  }

  /** Returns a random weight; ~15 % of calls produce a high-outlier weight (3–5×). */
  private double workNodeWeight() {
    final double baseWeight = 0.5 + rng.nextDouble();
    return rng.nextDouble() < 0.15 ? baseWeight * (3.0 + rng.nextDouble() * 2.0) : baseWeight;
  }
}
