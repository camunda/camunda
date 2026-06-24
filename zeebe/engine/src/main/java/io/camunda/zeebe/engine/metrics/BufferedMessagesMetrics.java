/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.EngineMetricsDoc.BUFFERED_MESSAGES;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

public class BufferedMessagesMetrics {

  private final StatefulGauge bufferedMessageCount;

  public BufferedMessagesMetrics(final MeterRegistry meterRegistry) {
    bufferedMessageCount =
        StatefulGauge.builder(BUFFERED_MESSAGES.getName())
            .description(BUFFERED_MESSAGES.getDescription())
            .register(meterRegistry);
  }

  /**
   * Be wary of calling this from outside the stream processing actor, you may end up with the
   * incorrect number due to race conditions.
   */
  public void setBufferedMessagesCounter(final long counter) {
    bufferedMessageCount.set(counter);
  }
}
