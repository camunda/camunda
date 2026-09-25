/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import io.camunda.zeebe.metrics.RequestOutcomeRecorder;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseChecker extends Thread {
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Worker.class), Duration.ofSeconds(5));

  private final BlockingQueue<Future<?>> futures;
  private final RequestOutcomeRecorder requestOutcomeRecorder;
  private volatile boolean shuttingDown = false;

  public ResponseChecker(
      final BlockingQueue<Future<?>> futures, final RequestOutcomeRecorder requestOutcomeRecorder) {
    this.futures = futures;
    this.requestOutcomeRecorder = requestOutcomeRecorder;
  }

  @Override
  public void run() {
    while (!shuttingDown) {
      final Future<?> future;
      try {
        future = futures.take();
      } catch (final InterruptedException e) {
        // ignore and retry
        continue;
      }
      try {
        future.get();
        requestOutcomeRecorder.record(Worker.COMPLETE_JOB, null);
      } catch (final InterruptedException e) {
        // ignore and retry
      } catch (final ExecutionException e) {
        requestOutcomeRecorder.record(Worker.COMPLETE_JOB, e);
        final Throwable cause = e.getCause();
        if (cause instanceof StatusRuntimeException) {
          final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) cause;
          if (statusRuntimeException.getStatus().getCode() != Code.RESOURCE_EXHAUSTED) {
            // we don't want to flood the log
            THROTTLED_LOGGER.warn("Request failed", e);
          }
        }
      }
    }
  }

  public void close() {
    shuttingDown = true;
    interrupt();
  }
}
