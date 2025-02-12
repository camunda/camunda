/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.StatefulMeterRegistry;
import java.util.concurrent.atomic.AtomicLong;

public class BufferedMessagesMetrics {

  private final AtomicLong bufferedMessageCount;

  public BufferedMessagesMetrics(final StatefulMeterRegistry meterRegistry) {
    bufferedMessageCount = meterRegistry.newLongGauge(EngineMetricsDoc.BUFFERED_MESSAGES).state();
  }

  /**
   * Be wary of calling this from outside the stream processing actor, you may end up with the
   * incorrect number due to race conditions.
   */
  public void setBufferedMessagesCounter(final long counter) {
    bufferedMessageCount.set(counter);
  }
}
