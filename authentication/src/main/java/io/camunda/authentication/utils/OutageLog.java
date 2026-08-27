/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.utils;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/**
 * Collapses a failure that recurs on every request into one WARN per outage. Without this, a
 * degraded dependency on a per-request path floods the log at a rate set by incoming traffic, which
 * buries the outage rather than reporting it.
 *
 * <p>Hold one instance per independently failing thing, so an outage of one does not suppress the
 * report of another.
 */
public final class OutageLog {

  private final Logger log;
  private final AtomicBoolean degraded = new AtomicBoolean();

  public OutageLog(final Logger log) {
    this.log = log;
  }

  /**
   * Logs at WARN for the failure that starts an outage and at DEBUG for every failure until {@link
   * #recovery} closes it.
   */
  public void failure(final String format, final Object... args) {
    if (degraded.compareAndSet(false, true)) {
      log.warn(format, args);
    } else {
      log.debug(format, args);
    }
  }

  /** Logs at INFO for the first success after an outage, and stays silent when none was open. */
  public void recovery(final String format, final Object... args) {
    if (degraded.compareAndSet(true, false)) {
      log.info(format, args);
    }
  }
}
