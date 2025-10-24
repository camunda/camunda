/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.startup;

import static java.util.Collections.singletonList;
import static org.slf4j.MDC.*;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
 * <p><strong>Error handling</strong>
 *
 * <ul>
 *   <li>Exceptions that occur during startup/shutdown are propagated via the {@code ActorFuture}.
 *       They are also wrapped.
 *   <li>If the exceptions is related to a certain step, it is wrapped in a {@code
 *       StartupProcessStepException}, which is then added as suppressed exception to a {@code
 *       StartupProcessException}, which is returned by the future
 *   <li>If the exception is not related to a step (e.g. startup is aborted due to concurrent
 *       shutdown), this is propagated as a plain {@code StartupProcessException}
 *   <li>{@code}IllegalStateExceptions are not propagated via the future but thrown directly to the
 *       caller
 * </ul>
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

  private final Logger logger;
  private final Queue<StartupStep<CONTEXT>> steps;
  private final Deque<StartupStep<CONTEXT>> startedSteps = new ArrayDeque<>();

  private boolean startupCalled = false;
  private ActorFuture<CONTEXT> shutdownFuture;
  private ActorFuture<CONTEXT> startupFuture;
  private Map<String, String> loggingContext = Map.of();

  /**
   * Constructs the startup process
   *
   * @param steps the steps to execute; must not be {@code null}
   */
  public StartupProcess(final List<StartupStep<CONTEXT>> steps) {
    this(LoggerFactory.getLogger(StartupProcess.class), steps);
  }

  /**
   * Constructs the startup process
   *
   * @param logger the logger to use for messages related to the startup process; must not be {@code
   *     null}
   * @param steps the steps to execute; must not be {@code null}
   */
  public StartupProcess(final Logger logger, final List<? extends StartupStep<CONTEXT>> steps) {
    this.steps = new ArrayDeque<>(Objects.requireNonNull(steps));
    this.logger = Objects.requireNonNull(logger);
  }

  /**
   * @param loggingContext temporary logging context to set during execution of the steps. For
   *     example, if the actor that executes this process is shared by different partitions, you can
   *     provide this key to temporarily set the partitionId in the MDC which will be picked by the
   *     logging during the execution of the steps. must not be {@code null}
   * @param logger the logger to use for messages related to the startup process; must not be {@code
   *     * null}
   * @param steps the steps to execute; must not be {@code null}
   */
  public StartupProcess(
      final Map<String, String> loggingContext,
      final Logger logger,
      final List<? extends StartupStep<CONTEXT>> steps) {
    this.steps = new ArrayDeque<>(Objects.requireNonNull(steps));
    this.logger = Objects.requireNonNull(logger);
    this.loggingContext = Objects.requireNonNull(loggingContext);
  }

  /**
   * Executes the startup logic
   *
   * @param concurrencyControl the startup context at the start of this step
   * @param context the startup context at the start of this step
   * @return future with startup context at the end of this step
   */
  public ActorFuture<CONTEXT> startup(
      final ConcurrencyControl concurrencyControl, final CONTEXT context) {

    final var result = concurrencyControl.<CONTEXT>createFuture();
    concurrencyControl.run(() -> startupSynchronized(concurrencyControl, context, result));

    return result;
  }

  /**
   * Executes the shutdown logic
   *
   * @param context the shutdown context at the start of this step
   * @return future with the shutdown context at the end of this step.
   */
  public ActorFuture<CONTEXT> shutdown(
      final ConcurrencyControl concurrencyControl, final CONTEXT context) {
    final var result = concurrencyControl.<CONTEXT>createFuture();
    concurrencyControl.run(() -> shutdownSynchronized(concurrencyControl, context, result));

    return result;
  }

  private void startupSynchronized(
      final ConcurrencyControl concurrencyControl,
      final CONTEXT context,
      final ActorFuture<CONTEXT> startupFuture) {
    setCustomMDC();
    logger.debug("Startup was called with context: {}", context);
    clearCustomMDC();
    if (startupCalled) {
      throw new IllegalStateException("startup(...) must only be called once");
    }
    startupCalled = true;
    this.startupFuture = startupFuture;

    // reset future when we are done
    concurrencyControl.runOnCompletion(startupFuture, (result, error) -> this.startupFuture = null);

    final var stepsToStart = new ArrayDeque<>(steps);

    proceedWithStartupSynchronized(concurrencyControl, stepsToStart, context, startupFuture);
  }

  private void proceedWithStartupSynchronized(
      final ConcurrencyControl concurrencyControl,
      final Queue<StartupStep<CONTEXT>> stepsToStart,
      final CONTEXT context,
      final ActorFuture<CONTEXT> startupFuture) {
    if (stepsToStart.isEmpty()) {
      startupFuture.complete(context);
      setCustomMDC();
      logger.debug("Finished startup process");
      clearCustomMDC();
    } else if (shutdownFuture != null) {
      setCustomMDC();
      logger.info("Aborting startup process because shutdown was called");
      clearCustomMDC();
      startupFuture.completeExceptionally(
          new StartupProcessShutdownException(
              "Aborting startup process because shutdown was called"));
    } else {
      final var stepToStart = stepsToStart.poll();
      startedSteps.push(stepToStart);

      logCurrentStepSynchronized("Startup", stepToStart);

      final var before = System.nanoTime();
      final var stepStartupFuture = stepToStart.startup(context);

      concurrencyControl.runOnCompletion(
          stepStartupFuture,
          (contextReturnedByStep, error) -> {
            final var completedAt = System.nanoTime();
            logger.debug(
                "StartupStep {} completed (error={}) took {} millis ",
                stepToStart.getName(),
                error != null,
                (completedAt - before) / 1e6);
            if (error != null) {
              completeStartupFutureExceptionallySynchronized(startupFuture, stepToStart, error);
            } else {
              proceedWithStartupSynchronized(
                  concurrencyControl, stepsToStart, contextReturnedByStep, startupFuture);
            }
          });
    }
  }

  private void completeStartupFutureExceptionallySynchronized(
      final ActorFuture<CONTEXT> startupFuture,
      final StartupStep<CONTEXT> stepToStart,
      final Throwable error) {
    setCustomMDC();
    logger.warn(
        "Aborting startup process due to exception during step " + stepToStart.getName(), error);
    clearCustomMDC();
    startupFuture.completeExceptionally(
        aggregateExceptionsSynchronized(
            "Startup",
            singletonList(new StartupProcessStepException(stepToStart.getName(), error))));
  }

  private void shutdownSynchronized(
      final ConcurrencyControl concurrencyControl,
      final CONTEXT context,
      final ActorFuture<CONTEXT> resultFuture) {
    setCustomMDC();
    logger.debug("Shutdown was called with context: {}", context);
    clearCustomMDC();
    if (shutdownFuture == null) {
      shutdownFuture = resultFuture;

      if (startupFuture != null) {
        concurrencyControl.runOnCompletion(
            startupFuture,
            (contextReturnedByStartup, error) -> {
              final var contextForShutdown = error == null ? contextReturnedByStartup : context;

              proceedWithShutdownSynchronized(
                  concurrencyControl, contextForShutdown, shutdownFuture, new ArrayList<>());
            });
      } else {
        proceedWithShutdownSynchronized(
            concurrencyControl, context, shutdownFuture, new ArrayList<>());
      }
    } else {
      setCustomMDC();
      logger.info("Shutdown already in progress");
      clearCustomMDC();

      concurrencyControl.runOnCompletion(
          shutdownFuture,
          (contextReturnedByShutdown, error) -> {
            if (error != null) {
              resultFuture.completeExceptionally(error);
            } else {
              resultFuture.complete(contextReturnedByShutdown);
            }
          });
    }
  }

  private void proceedWithShutdownSynchronized(
      final ConcurrencyControl concurrencyControl,
      final CONTEXT context,
      final ActorFuture<CONTEXT> shutdownFuture,
      final List<StartupProcessStepException> collectedExceptions) {
    if (startedSteps.isEmpty()) {
      completeShutdownFutureSynchronized(context, shutdownFuture, collectedExceptions);
    } else {
      final var stepToShutdown = startedSteps.pop();

      logCurrentStepSynchronized("Shutdown", stepToShutdown);

      final var shutdownStepFuture = stepToShutdown.shutdown(context);

      concurrencyControl.runOnCompletion(
          shutdownStepFuture,
          (contextReturnedByShutdown, error) -> {
            final CONTEXT contextToUse;
            if (error != null) {
              collectedExceptions.add(
                  new StartupProcessStepException(stepToShutdown.getName(), error));
              contextToUse = context;
            } else {
              contextToUse = contextReturnedByShutdown;
            }

            proceedWithShutdownSynchronized(
                concurrencyControl, contextToUse, shutdownFuture, collectedExceptions);
          });
    }
  }

  private void completeShutdownFutureSynchronized(
      final CONTEXT context,
      final ActorFuture<CONTEXT> shutdownFuture,
      final List<StartupProcessStepException> collectedExceptions) {
    if (collectedExceptions.isEmpty()) {
      shutdownFuture.complete(context);
      setCustomMDC();
      logger.debug("Finished shutdown process");
      clearCustomMDC();
    } else {
      final var umbrellaException =
          aggregateExceptionsSynchronized("Shutdown", collectedExceptions);
      shutdownFuture.completeExceptionally(umbrellaException);
      setCustomMDC();
      logger.warn(umbrellaException.getMessage(), umbrellaException);
    }
  }

  private Throwable aggregateExceptionsSynchronized(
      final String operation, final List<StartupProcessStepException> exceptions) {
    final var failedSteps =
        exceptions.stream()
            .map(StartupProcessStepException::getStepName)
            .collect(Collectors.toList());
    final var message =
        String.format(
            "%s failed in the following steps: %s. See suppressed exceptions for details.",
            operation, failedSteps);

    final var exception = new StartupProcessException(message);
    exceptions.forEach(exception::addSuppressed);
    return exception;
  }

  private void logCurrentStepSynchronized(final String process, final StartupStep<CONTEXT> step) {
    setCustomMDC();
    logger.info(process + " " + step.getName());
    clearCustomMDC();
  }

  private void setCustomMDC() {
    loggingContext.forEach(
        (key, value) -> {
          if (key != null && value != null) {
            MDC.put(key, value);
          }
        });
  }

  private void clearCustomMDC() {
    loggingContext
        .keySet()
        .forEach(
            key -> {
              if (key != null) {
                MDC.remove(key);
              }
            });
  }
}
