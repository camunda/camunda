/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.agrona.LangUtil;

public final class FutureUtil {
  /**
   * Invokes Future.get() returning the result of the invocation. Transforms checked exceptions into
   * RuntimeExceptions to accommodate programmer laziness.
   */
  public static <T> T join(final Future<T> f) {
    try {
      return f.get();
    } catch (final Exception e) {
      // NOTE: here we actually want to use rethrowUnchecked
      LangUtil.rethrowUnchecked(e);
    }

    return null;
  }

  public static <T> T join(final Future<T> f, final long timeout, final TimeUnit timeUnit) {
    try {
      return f.get(timeout, timeUnit);
    } catch (final Exception e) {
      // NOTE: here we actually want to use rethrowUnchecked
      LangUtil.rethrowUnchecked(e);
    }

    return null;
  }

  public static Runnable wrap(final Future<?> future) {
    return () -> {
      try {
        future.get();
      } catch (final Exception e) {
        LangUtil.rethrowUnchecked(e);
      }
    };
  }
}
