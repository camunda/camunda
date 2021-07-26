/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.startup;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractStartupStep<CONTEXT> implements StartupStep<CONTEXT> {

  private boolean startupCalled = false;
  private CompletableFuture<CONTEXT> startupFuture;
  private CompletableFuture<CONTEXT> shutdownFuture;

  /**
   * Guarded version of the startup method. Guarded means in this case that this method will only be
   * called if the step is in the correct state
   */
  protected abstract CompletableFuture<CONTEXT> startupGuarded(final CONTEXT context);
  /**
   * Guarded version of the shutdown method. Guarded means in this case that this method will only
   * be called if the step is in the correct state
   */
  protected abstract CompletableFuture<CONTEXT> shutdownGuarded(final CONTEXT context);

  @Override
  public final synchronized CompletableFuture<CONTEXT> startup(final CONTEXT context) {
    if (startupCalled) {
      throw new IllegalStateException("startup(...) must only be called once");
    }
    startupCalled = true;
    startupFuture = startupGuarded(context);
    if (startupFuture == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "startupGuarded(...) did return null, instead of a future object"));
    }
    return startupFuture;
  }

  @Override
  public final synchronized CompletableFuture<CONTEXT> shutdown(final CONTEXT context) {
    if (!startupCalled) {
      throw new IllegalStateException("shutdown(...) can only be called after startup(...)");
    }

    if (shutdownFuture == null) {
      // check if startup has completed
      if (startupFuture.isDone()) {
        shutdownFuture = callGuardedMethod(context);
      } else {
        // if it has not, chain shutdown to startup so that it will run immediately afterwards
        shutdownFuture =
            startupFuture.handle(
                (contextReturnedByStartup, startupError) -> {
                  /* Here we figure out which context object to use. If we were called while shutdown
                   * was running, and shutdown completed successfully, we use the context returned
                   * by startup, because it is the most recent one.
                   * If startup completed exceptionally, we use the context we were called with,
                   * because it represents the last consistent state before the error occurred
                   */
                  final var contextToUSe =
                      startupError == null ? contextReturnedByStartup : context;
                  return callGuardedMethod(contextToUSe).join();
                });
      }
    }

    return shutdownFuture;
  }

  private CompletableFuture<CONTEXT> callGuardedMethod(final CONTEXT context) {
    final var result = shutdownGuarded(context);

    if (result == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "shutdownGuarded(...) did return null, instead of a future object"));
    }
    return result;
  }
}
