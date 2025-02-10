/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.util.CloseableSilently;
import org.agrona.CloseHelper;

/**
 * Represents an in-flight append. Updates metrics and backpressure limits after being {@link
 * InFlightAppend#start(long) started} and handles callbacks from the log storage. Commit and write
 * errors are propagated to an {@link AppendErrorHandler}.
 */
public final class InFlightAppend implements AppendListener {

  private final AppendErrorHandler errorHandler;
  private final Limiter.Listener limiter;
  private final AppenderMetrics metrics;
  private CloseableSilently writeTimer;
  private CloseableSilently commitTimer;
  private long position;

  public InFlightAppend(
      final AppendErrorHandler errorHandler,
      final Limiter.Listener limiter,
      final AppenderMetrics metrics) {
    this.errorHandler = errorHandler;
    this.limiter = limiter;
    this.metrics = metrics;
  }

  @Override
  public void onWrite(final long address) {
    writeTimer.close();
    metrics.setLastWrittenPosition(position);
  }

  @Override
  public void onWriteError(final Throwable error) {
    errorHandler.onWriteError(error);
    metrics.decreaseInflight();
    CloseHelper.quietCloseAll(commitTimer, writeTimer);
    limiter.onDropped();
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

  @Override
  public void onCommitError(final long address, final Throwable error) {
    errorHandler.onCommitError(error);
    metrics.decreaseInflight();
    limiter.onDropped();
  }

  public InFlightAppend start(final long position) {
    this.position = position;
    writeTimer = metrics.startWriteTimer();
    commitTimer = metrics.startCommitTimer();
    metrics.increaseInflight();
    metrics.increaseTriedAppends();
    return this;
  }

  public void discard() {
    limiter.onIgnore();
  }
}
