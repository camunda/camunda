/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter.Listener;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.prometheus.client.Histogram;
import java.util.List;

public final class InFlightEntry {
  final LogStreamMetrics metrics;
  List<LogAppendEntryMetadata> entryMetadata;
  Listener requestListener;
  Histogram.Timer writeTimer;
  Histogram.Timer commitTimer;

  public InFlightEntry(
      final LogStreamMetrics metrics,
      final List<LogAppendEntryMetadata> entryMetadata,
      final Listener requestListener) {
    this.metrics = metrics;
    this.entryMetadata = entryMetadata;
    this.requestListener = requestListener;
    writeTimer = null;
    commitTimer = null;
  }

  public void onAppend() {
    writeTimer = metrics.startWriteTimer();
    commitTimer = metrics.startCommitTimer();
    metrics.increaseInflightAppends();
    if (requestListener != null) {
      metrics.increaseInflightRequests();
    }
  }

  public void onWrite() {
    entryMetadata.forEach(
        metadata ->
            metrics.recordAppendedEntry(
                1, metadata.recordType(), metadata.valueType(), metadata.intent()));
    entryMetadata = null;
    if (writeTimer != null) {
      writeTimer.close();
      writeTimer = null;
    }
  }

  public void onCommit() {
    metrics.decreaseInflightAppends();
    if (commitTimer != null) {
      commitTimer.close();
      commitTimer = null;
    }
  }

  public void onProcessed() {
    if (requestListener != null) {
      requestListener.onSuccess();
      metrics.decreaseInflightRequests();
      requestListener = null;
    }
  }
}
