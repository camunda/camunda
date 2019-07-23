/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.ZbLogger;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.slf4j.Logger;

public class TestUtil {

  public static final int MAX_RETRIES = 100;
  private static final Logger LOG = new ZbLogger(TestUtil.class);

  public static <T> Invocation<T> doRepeatedly(Callable<T> callable) {
    return new Invocation<>(callable);
  }

  public static Invocation<Void> doRepeatedly(Runnable runnable) {
    return new Invocation<>(
        () -> {
          runnable.run();
          return null;
        });
  }

  public static void waitUntil(final BooleanSupplier condition) {
    doRepeatedly(() -> null).until((r) -> condition.getAsBoolean());
  }

  public static void waitUntil(
      final BooleanSupplier condition, final String message, final Object... args) {
    doRepeatedly(() -> null).until((r) -> condition.getAsBoolean(), message, args);
  }

  public static void waitUntil(final BooleanSupplier condition, final int retries) {
    doRepeatedly(() -> null).until((r) -> condition.getAsBoolean(), retries);
  }

  public static void waitUntil(
      final BooleanSupplier condition,
      final int retries,
      final String message,
      final Object... args) {
    doRepeatedly(() -> null).until((r) -> condition.getAsBoolean(), retries, message, args);
  }

  public static class Invocation<T> {
    protected Callable<T> callable;

    public Invocation(Callable<T> callable) {
      this.callable = callable;
    }

    public T until(Function<T, Boolean> resultCondition) {
      return until(resultCondition, (e) -> false);
    }

    public T until(
        Function<T, Boolean> resultCondition, final String message, final Object... args) {
      return until(resultCondition, (e) -> false, message, args);
    }

    public T until(Function<T, Boolean> resultCondition, final int retries) {
      return until(resultCondition, (e) -> false, retries);
    }

    public T until(
        Function<T, Boolean> resultCondition,
        final int retries,
        final String message,
        final Object... args) {
      return until(resultCondition, (e) -> false, retries, message, args);
    }

    public T until(
        final Function<T, Boolean> resultCondition,
        Function<Exception, Boolean> exceptionCondition) {
      final T result =
          whileConditionHolds(
              (t) -> !resultCondition.apply(t), (e) -> !exceptionCondition.apply(e));

      assertThat(resultCondition.apply(result)).isTrue();

      return result;
    }

    public T until(
        final Function<T, Boolean> resultCondition,
        Function<Exception, Boolean> exceptionCondition,
        final String message,
        final Object... args) {
      final T result =
          whileConditionHolds(
              (t) -> !resultCondition.apply(t), (e) -> !exceptionCondition.apply(e));

      assertThat(resultCondition.apply(result)).withFailMessage(message, args).isTrue();

      return result;
    }

    public T until(
        final Function<T, Boolean> resultCondition,
        Function<Exception, Boolean> exceptionCondition,
        final int retries) {
      final T result =
          whileConditionHolds(
              (t) -> !resultCondition.apply(t), (e) -> !exceptionCondition.apply(e), retries);

      assertThat(resultCondition.apply(result)).isTrue();

      return result;
    }

    public T until(
        final Function<T, Boolean> resultCondition,
        Function<Exception, Boolean> exceptionCondition,
        final int retries,
        final String message,
        final Object... args) {
      final T result =
          whileConditionHolds(
              (t) -> !resultCondition.apply(t), (e) -> !exceptionCondition.apply(e), retries);

      assertThat(resultCondition.apply(result)).withFailMessage(message, args).isTrue();

      return result;
    }

    public T whileConditionHolds(Function<T, Boolean> resultCondition) {
      return whileConditionHolds(resultCondition, (e) -> true);
    }

    public T whileConditionHolds(Function<T, Boolean> resultCondition, final int retires) {
      return whileConditionHolds(resultCondition, (e) -> true, retires);
    }

    public T whileConditionHolds(
        Function<T, Boolean> resultCondition, Function<Exception, Boolean> exceptionCondition) {
      return whileConditionHolds(resultCondition, exceptionCondition, MAX_RETRIES);
    }

    public T whileConditionHolds(
        Function<T, Boolean> resultCondition,
        Function<Exception, Boolean> exceptionCondition,
        final int retries) {
      int numTries = 0;

      T result;

      do {
        result = null;

        try {
          if (numTries > 0) {
            Thread.sleep(100L);
          }

          result = callable.call();
        } catch (Exception e) {

          if (!exceptionCondition.apply(e)) {
            throw new RuntimeException("Unexpected exception while checking condition", e);
          } else {
            LOG.error("Exception caught, will retry.", e);
          }
        }

        numTries++;
      } while (numTries < retries && resultCondition.apply(result));

      return result;
    }
  }
}
