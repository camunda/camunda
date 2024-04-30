/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.prometheus.client.Histogram;

/**
 * Represents an in-flight append. Updates metrics and backpressure limits after being {@link
 * InFlightAppend#start(long) started} and handles callbacks from the log storage.
 */
public final class InFlightAppend implements AppendListener {

  private final Limiter.Listener limiter;
  private final LogStreamMetrics metrics;
  private Histogram.Timer writeTimer;
  private Histogram.Timer commitTimer;
  private long position;

  public InFlightAppend(final Limiter.Listener limiter, final LogStreamMetrics metrics) {
    this.limiter = limiter;
    this.metrics = metrics;
  }

  @Override
  public void onWrite(final long address) {
    writeTimer.close();
    metrics.setLastWrittenPosition(position);
  }

  @Override
  public void onCommit(final long address) {
    metrics.decreaseInflight();
    metrics.setLastCommittedPosition(position);
    if (commitTimer != null) {
      commitTimer.close();
    }
    limiter.onSuccess();
  }

  public InFlightAppend start(final long position) {
    this.position = position;
    writeTimer = metrics.startWriteTimer();
    commitTimer = metrics.startCommitTimer();
    metrics.increaseInflight();
    metrics.increaseTriedAppends();
    return this;
  }
}
