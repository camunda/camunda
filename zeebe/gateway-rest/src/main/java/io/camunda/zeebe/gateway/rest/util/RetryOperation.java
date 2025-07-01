/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//
// Taken from https://stackoverflow.com/questions/13239972/how-do-you-implement-a-re-try-catch and
// slightly modified
//
public final class RetryOperation<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RetryOperation.class);
  private final RetryConsumer<T> retryConsumer;
  private final int noOfRetry;
  private final int delayInterval;
  private final TimeUnit timeUnit;
  private final RetryPredicate<T> retryPredicate;
  private final List<Class<? extends Throwable>> exceptionList;
  private final String message;

  private RetryOperation(
      final RetryConsumer<T> retryConsumer,
      final int noOfRetry,
      final int delayInterval,
      final TimeUnit timeUnit,
      final RetryPredicate<T> retryPredicate,
      final List<Class<? extends Throwable>> exceptionList,
      final String message) {
    this.retryConsumer = retryConsumer;
    this.noOfRetry = noOfRetry;
    this.delayInterval = delayInterval;
    this.timeUnit = timeUnit;
    this.retryPredicate = retryPredicate;
    this.exceptionList = exceptionList;
    this.message = message;
  }

  public static <T> OperationBuilder<T> newBuilder() {
    return new OperationBuilder<>();
  }

  public T retry() throws Exception {
    T result = null;
    int retries = 0;
    while (retries < noOfRetry) {
      try {
        result = retryConsumer.evaluate();
        if (Objects.nonNull(retryPredicate)) {
          final boolean shouldItRetry = retryPredicate.shouldRetry(result);
          if (shouldItRetry) {
            retries = increaseRetryCountAndSleep(retries);
          } else {
            return result;
          }
        } else {
          // no retry condition defined, no exception thrown. This is the desired result.
          return result;
        }
      } catch (final Exception e) {
        LOGGER.warn(String.format("Retry Operation %s failed: %s", message, e.getMessage()), e);
        retries = handleException(retries, e);
      }
    }
    return result;
  }

  private int handleException(int retries, final Exception e) throws Exception {
    if (exceptionList.isEmpty()
        || exceptionList.stream().anyMatch(ex -> ex.isAssignableFrom(e.getClass()))) {
      // exception is accepted, continue retry.
      retries = increaseRetryCountAndSleep(retries);
      if (retries == noOfRetry) {
        // evaluation is throwing exception, no more retry left. Throw it.
        throw e;
      }
    } else {
      // unexpected exception, no retry required. Throw it.
      throw e;
    }
    return retries;
  }

  private int increaseRetryCountAndSleep(int retries) {
    retries++;
    if (retries < noOfRetry && delayInterval > 0) {
      try {
        if (retries % 20 == 0) {
          LOGGER.info(
              "{} - Waiting {} {}. {}/{}", message, delayInterval, timeUnit, retries, noOfRetry);
        } else {
          LOGGER.debug(
              "{} - Waiting {} {}. {}/{}", message, delayInterval, timeUnit, retries, noOfRetry);
        }
        timeUnit.sleep(delayInterval);
      } catch (final InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
    }
    return retries;
  }

  public static final class OperationBuilder<T> {
    private RetryConsumer<T> iRetryConsumer;
    private int iNoOfRetry;
    private int iDelayInterval;
    private TimeUnit iTimeUnit;
    private RetryPredicate<T> iRetryPredicate;
    private Class<? extends Throwable>[] exceptionClasses;
    private String message = "";

    private OperationBuilder() {}

    public OperationBuilder<T> retryConsumer(final RetryConsumer<T> retryConsumer) {
      iRetryConsumer = retryConsumer;
      return this;
    }

    public OperationBuilder<T> noOfRetry(final int noOfRetry) {
      iNoOfRetry = noOfRetry;
      return this;
    }

    public OperationBuilder<T> delayInterval(final int delayInterval, final TimeUnit timeUnit) {
      iDelayInterval = delayInterval;
      iTimeUnit = timeUnit;
      return this;
    }

    public OperationBuilder<T> retryPredicate(final RetryPredicate<T> retryPredicate) {
      iRetryPredicate = retryPredicate;
      return this;
    }

    @SafeVarargs
    public final OperationBuilder<T> retryOn(final Class<? extends Throwable>... exceptionClasses) {
      this.exceptionClasses = exceptionClasses;
      return this;
    }

    public OperationBuilder<T> message(final String message) {
      this.message = message;
      return this;
    }

    public RetryOperation<T> build() {
      if (Objects.isNull(iRetryConsumer)) {
        throw new RuntimeException("'#retryConsumer:RetryConsumer<T>' not set");
      }

      List<Class<? extends Throwable>> exceptionList = new ArrayList<>();
      if (Objects.nonNull(exceptionClasses) && exceptionClasses.length > 0) {
        exceptionList = Arrays.asList(exceptionClasses);
      }
      iNoOfRetry = iNoOfRetry == 0 ? 1 : iNoOfRetry;
      iTimeUnit = Objects.isNull(iTimeUnit) ? TimeUnit.MILLISECONDS : iTimeUnit;
      return new RetryOperation<>(
          iRetryConsumer,
          iNoOfRetry,
          iDelayInterval,
          iTimeUnit,
          iRetryPredicate,
          exceptionList,
          message);
    }
  }

  public static interface RetryConsumer<T> {
    T evaluate() throws Exception;
  }

  public static interface RetryPredicate<T> {
    boolean shouldRetry(T t);
  }
}
