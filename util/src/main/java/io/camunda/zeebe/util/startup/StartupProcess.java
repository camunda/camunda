/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.startup;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a number of steps in a startup/shutdown process.
 *
 * <p>On startup, steps are executed in the given order. If any step completes exceptionally, then
 * the subsequent steps are not executed and the startup future completes exceptionally. However, no
 * shutdown is triggered by this class. This can be done by the caller.
 *
 * <p>On shutdown, steps are executed in reverse order. If any shutdown step completes
 * exceptionally, subsequent steps will be executed and the exceptions of all steps are collected as
 * suppressed exceptions
 *
 * <p>Callers of this class must obey the following contract:
 *
 * <ul>
 *   <li>Shutdown must not be called before startup
 *   <li>Startup must be called at most once
 *   <li>Shutdown may be called more than once. The first call will trigger the shutdown and any
 *       subsequent calls do nothing
 *   <li>Shutdown may be called before the future of startup has completed. In that case, it will
 *       complete the current running startup step, cancel all subsequent startup step, complete the
 *       startup future with an exception and start the shutdown from the step that last completed
 * </ul>
 *
 * @param <CONTEXT> the startup/shutdown context
 */
public final class StartupProcess<CONTEXT> {

  private final Deque<StartupStep<CONTEXT>> steps;
  private final String processName;
  private final Logger logger;

  private boolean startupCalled = false;
  private final Stack<StartupStep<CONTEXT>> startedSteps = new Stack<>();
  private CompletableFuture<CONTEXT> shutdownFuture;

  public StartupProcess(final String processName, final List<StartupStep<CONTEXT>> steps) {
    this.steps = new ArrayDeque<>(Objects.requireNonNull(steps));

    this.processName = processName != null ? processName : "Undefined process";
    logger =
        LoggerFactory.getLogger(
            "io.camunda.zeebe.util.startup.StartupProcess(" + this.processName + ")");
  }

  /**
   * Executes the startup logic
   *
   * @param context the startup context at the start of this step
   * @return future with startup context at the end of this step
   */
  public synchronized CompletableFuture<CONTEXT> startup(final CONTEXT context) {
    logger.debug("Startup was called with context: " + context);
    if (startupCalled) {
      throw new IllegalStateException("startup(...) must only be called once");
    }
    startupCalled = true;

    final var startupFuture = new CompletableFuture<CONTEXT>();
    final var stepsToStart = new ArrayDeque<>(steps);

    logger.info("Starting startup process");
    proceedWithStartup(stepsToStart, context, startupFuture);

    return startupFuture;
  }

  private synchronized void proceedWithStartup(
      final Queue<StartupStep<CONTEXT>> stepsToStart,
      final CONTEXT context,
      final CompletableFuture<CONTEXT> startupFuture) {
    if (shutdownFuture != null) {
      logger.info("Aborting startup process because shutdown was called");
      startupFuture.completeExceptionally(
          new RuntimeException("Aborting startup process because shutdown was called"));
    } else if (stepsToStart.isEmpty()) {
      startupFuture.complete(context);
      logger.info("Finished startup process");
    } else {
      final var stepToStart = stepsToStart.poll();
      startedSteps.push(stepToStart);

      logCurrentStep("Startup", stepToStart);
      stepToStart
          .startup(context)
          .whenComplete(
              (contextReturnedByStep, error) -> {
                if (error != null) {
                  logger.warn(
                      "Aborting startup process due to exception during stage "
                          + stepToStart.getName(),
                      error);
                  startupFuture.completeExceptionally(error);
                } else {
                  proceedWithStartup(stepsToStart, contextReturnedByStep, startupFuture);
                }
              });
    }
  }

  /**
   * Executes the shutdown logic
   *
   * @param context the shutdown context at the start of this step
   * @return future with the shutdown context at the end of this step.
   */
  public synchronized CompletableFuture<CONTEXT> shutdown(final CONTEXT context) {
    logger.debug("Shutdown was called with context: " + context);
    if (shutdownFuture == null) {
      logger.info("Starting shutdown process");
      shutdownFuture = new CompletableFuture<>();

      proceedWithShutdown(context, shutdownFuture, new ArrayList<>());
    } else {
      logger.info("Shutdown already in progress");
    }
    return shutdownFuture;
  }

  private void proceedWithShutdown(
      final CONTEXT context,
      final CompletableFuture<CONTEXT> shutdownFuture,
      final List<ShutdownExceptionRecord> collectedExceptions) {
    if (startedSteps.isEmpty()) {
      completeShutdownFuture(context, shutdownFuture, collectedExceptions);
    } else {
      final var stepToShutdown = startedSteps.pop();

      logCurrentStep("Shutdown", stepToShutdown);
      stepToShutdown
          .shutdown(context)
          .whenComplete(
              (contextReturnedByShutdown, error) -> {
                final CONTEXT contextToUse;
                if (error != null) {
                  collectedExceptions.add(new ShutdownExceptionRecord(stepToShutdown, error));
                  contextToUse = context;
                } else {
                  contextToUse = contextReturnedByShutdown;
                }

                proceedWithShutdown(contextToUse, shutdownFuture, collectedExceptions);
              });
    }
  }

  private void completeShutdownFuture(
      final CONTEXT context,
      final CompletableFuture<CONTEXT> shutdownFuture,
      final List<ShutdownExceptionRecord> collectedExceptions) {
    if (collectedExceptions.isEmpty()) {
      shutdownFuture.complete(context);
      logger.info("Finished shutdown process");
    } else {
      final Throwable exception;
      if (collectedExceptions.size() == 1) {
        exception = collectedExceptions.get(0).getException();
      } else {
        final var details =
            collectedExceptions.stream()
                .map(entry -> entry.getStep().getName() + " " + entry.getException().getMessage())
                .collect(Collectors.joining("\n"));
        exception =
            new Exception(
                "Exceptions occurred during shutdown: \n"
                    + details
                    + "\nSee suppressed exceptions for more detail.");
        collectedExceptions.stream()
            .map(ShutdownExceptionRecord::getException)
            .forEach(exception::addSuppressed);
      }
      shutdownFuture.completeExceptionally(exception);
      logger.warn("Finished shutdown process with exception:", exception);
    }
  }

  private void logCurrentStep(final String process, final StartupStep<CONTEXT> step) {
    logger.info(process + " " + step.getName());
  }

  private static final class ShutdownExceptionRecord {
    private final StartupStep<?> step;
    private final Throwable exception;

    private ShutdownExceptionRecord(final StartupStep<?> step, final Throwable exception) {
      this.step = step;
      this.exception = exception;
    }

    private StartupStep<?> getStep() {
      return step;
    }

    public Throwable getException() {
      return exception;
    }
  }
}
