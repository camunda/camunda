/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import org.agrona.ErrorHandler;
import org.agrona.LangUtil;

/** Utility class for common tasks with {@link Lock} instances. */
public final class LockUtil {

  private static final ErrorHandler IGNORE_ERROR_HANDLER = error -> {};

  private LockUtil() {}

  /**
   * Runs the given operation only when the lock has been obtained. Locks interruptibly, meaning if
   * the thread is interrupted while waiting for the lock, the runnable is not executed.
   *
   * @param lock the lock to acquire
   * @param runnable the operation to run
   */
  public static void withLock(final Lock lock, final Runnable runnable) {
    withLock(lock, runnable, IGNORE_ERROR_HANDLER);
  }

  /**
   * Runs the given operation only when the lock has been obtained. Locks interruptibly, meaning if
   * the thread is interrupted while waiting for the lock, the runnable is not executed.
   *
   * @param lock the lock to acquire
   * @param callable the operation to run
   */
  public static <V> V withLock(final Lock lock, final Callable<V> callable) {
    return withLock(lock, callable, IGNORE_ERROR_HANDLER);
  }

  /**
   * Runs the given operation only when the lock has been obtained. Locks interruptibly, meaning if
   * the thread is interrupted while waiting for the lock, the runnable is not executed. If
   * interrupted, calls the error handler.
   *
   * @param lock the lock to acquire
   * @param runnable the operation to run
   * @param errorHandler called if an error occurs during interruption
   */
  public static void withLock(
      final Lock lock, final Runnable runnable, final ErrorHandler errorHandler) {
    try {
      lock.lockInterruptibly();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      errorHandler.onError(e);
      LangUtil.rethrowUnchecked(e);
      return;
    }

    try {
      runnable.run();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Runs the given operation only when the lock has been obtained. Locks interruptibly, meaning if
   * the thread is interrupted while waiting for the lock, the runnable is not executed. If
   * interrupted, calls the error handler.
   *
   * @param lock the lock to acquire
   * @param callable the operation to run
   * @param errorHandler called if an error occurs during interruption
   */
  public static <V> V withLock(
      final Lock lock, final Supplier<V> callable, final ErrorHandler errorHandler) {
    try {
      lock.lockInterruptibly();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      errorHandler.onError(e);
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    }

    try {
      return callable.get();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Runs the given operation only when the lock has been obtained. Locks interruptibly, meaning if
   * the thread is interrupted while waiting for the lock, the runnable is not executed. If
   * interrupted, calls the error handler.
   *
   * @param lock the lock to acquire
   * @param callable the operation to run
   * @param errorHandler called if an error occurs during interruption
   */
  public static <V> V withLock(
      final Lock lock, final Callable<V> callable, final ErrorHandler errorHandler) {
    try {
      lock.lockInterruptibly();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      errorHandler.onError(e);
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    }

    try {
      return callable.call();
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    } finally {
      lock.unlock();
    }
  }
}
