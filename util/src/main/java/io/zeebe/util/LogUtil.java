/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.MDC;

public final class LogUtil {
  /** see https://logback.qos.ch/manual/mdc.html */
  public static void doWithMDC(final Map<String, String> context, final Runnable r) {
    final Map<String, String> currentContext = MDC.getCopyOfContextMap();
    MDC.setContextMap(context);

    try {
      r.run();
    } finally {
      if (currentContext != null) {
        MDC.setContextMap(currentContext);
      } else {
        MDC.clear();
      }
    }
  }

  public static void catchAndLog(final Logger log, final ThrowingRunnable r) {
    try {
      r.run();
    } catch (final Throwable e) {
      log.error(e.getMessage(), e);
    }
  }

  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Exception;
  }
}
