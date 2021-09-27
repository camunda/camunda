/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.jar;

import io.camunda.zeebe.util.CheckedRunnable;
import java.util.concurrent.Callable;
import org.agrona.LangUtil;

/**
 * A collection of utilities to run an arbitrary {@link Runnable} with a specific thread context
 * class loader. This is required when side loading external code via the {@link
 * ExternalJarClassLoader}, as that code may be using the {@link Thread#getContextClassLoader()}.
 *
 * <p>As the same thread may be reused, it's also important to reset the thread afterwards to avoid
 * operations being run on the wrong class loader.
 */
public final class ThreadContextUtil {

  /**
   * Executes the given {@code runnable}, swapping the thread context class loader for the given
   * class loader, and swapping it back with the previous class loader afterwards.
   *
   * @param runnable the operation to execute
   * @param classLoader the class loader to temporarily assign to the current thread's context class
   *     loader
   */
  public static void runWithClassLoader(final Runnable runnable, final ClassLoader classLoader) {
    try {
      runCheckedWithClassLoader(runnable::run, classLoader);
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  /**
   * Executes the given {@code runnable}, swapping the thread context class loader for the given
   * class loader, and swapping it back with the previous class loader afterwards.
   *
   * <p>Use this method if you want your operation to throw exceptions; the class loader is
   * guaranteed to be reset even if an exception is thrown.
   *
   * @param runnable the operation to execute
   * @param classLoader the class loader to temporarily assign to the current thread's context class
   *     loader
   */
  public static void runCheckedWithClassLoader(
      final CheckedRunnable runnable, final ClassLoader classLoader) throws Exception {
    final var currentThread = Thread.currentThread();
    final var contextClassLoader = currentThread.getContextClassLoader();

    try {
      currentThread.setContextClassLoader(classLoader);
      runnable.run();
    } finally {
      currentThread.setContextClassLoader(contextClassLoader);
    }
  }

  /**
   * Executes the given {@code callable}, swapping the thread context class loader for the given
   * class loader, and swapping it back with the previous class loader afterwards.
   *
   * @param callable the operation to execute
   * @param classLoader the class loader to temporarily assign to the current thread's context class
   *     loader
   */
  public static <V> V callWithClassLoader(final Callable<V> callable, final ClassLoader classLoader)
      throws Exception {
    final var currentThread = Thread.currentThread();
    final var contextClassLoader = currentThread.getContextClassLoader();

    try {
      currentThread.setContextClassLoader(classLoader);
      return callable.call();
    } finally {
      currentThread.setContextClassLoader(contextClassLoader);
    }
  }
}
