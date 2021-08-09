/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.startup;

import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.ActorTaskSchedulingService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a number of steps in a startup/shutdown process.
 *
 * <p>On startup, steps are executed in the given order. If any step completes exceptionally, then
 * the subsequent steps are not executed and the startup completes exceptionally. However, no
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

  private final Logger logger;
  private final ActorTaskSchedulingService actorTaskSchedulingService;
  private final Deque<StartupStep<CONTEXT>> steps;

  private final Stack<StartupStep<CONTEXT>> startedSteps = new Stack<>();
  private boolean startupCalled = false;
  private boolean shutdownCalled = false;
  private AggregateConsumer<Either<Throwable, CONTEXT>> startupCallbackAggregate;
  private AggregateConsumer<Either<Throwable, CONTEXT>> shutdownCallbackAggregate;

  /**
   * Constructs the startup process
   *
   * @param steps the steps to execute; must not be {@code null}
   */
  public StartupProcess(
      final ActorTaskSchedulingService actorTaskSchedulingService,
      final List<StartupStep<CONTEXT>> steps) {
    this(
        LoggerFactory.getLogger("io.camunda.zeebe.util.startup"),
        actorTaskSchedulingService,
        steps);
  }

  /**
   * Constructs the startup process
   *
   * @param logger the logger to use for messages related to the startup process; must not be {@code
   *     null}
   * @param steps the steps to execute; must not be {@code null}
   */
  public StartupProcess(
      final Logger logger,
      final ActorTaskSchedulingService actorTaskSchedulingService,
      final List<StartupStep<CONTEXT>> steps) {
    this.logger = Objects.requireNonNull(logger);
    this.actorTaskSchedulingService = Objects.requireNonNull(actorTaskSchedulingService);
    this.steps = new ArrayDeque<>(Objects.requireNonNull(steps));
  }

  public void shutdown(final CONTEXT context, final Consumer<Either<Throwable, CONTEXT>> callback) {
    actorTaskSchedulingService.submit(() -> shutdownSynchronized(context, callback));
  }

  private void shutdownSynchronized(
      final CONTEXT context, final Consumer<Either<Throwable, CONTEXT>> callback) {
    logger.debug("Shutdown was called with context: " + context);

    /* signal that shutdown was called; this is read by the startup process to abort a concurrent
    startup process*/
    shutdownCalled = true;

    if (shutdownCallbackAggregate == null) {
      logger.info("Starting shutdown process");

      // check if startup is concurrently running
      if (startupCallbackAggregate != null) {
        // if it is, trigger the shutdown after the current startup step has completed
        startupCallbackAggregate.addConsumer(
            (either) ->
                actorTaskSchedulingService.submit(
                    () -> {
                      final var contextToUse = either.isRight() ? either.get() : context;
                      startShutdownSynchronized(contextToUse, callback);
                    }));
      } else {
        startShutdownSynchronized(context, callback);
      }
    } else {
      logger.info("Shutdown already in progress");
      shutdownCallbackAggregate.addConsumer(callback);
    }
  }

  private void startShutdownSynchronized(
      final CONTEXT context, final Consumer<Either<Throwable, CONTEXT>> callback) {
    shutdownCallbackAggregate = new AggregateConsumer<>();
    shutdownCallbackAggregate.addConsumer(callback);

    proceedWithShutdownSynchronized(context, shutdownCallbackAggregate, new ArrayList<>());
  }

  public void startup(final CONTEXT context, final Consumer<Either<Throwable, CONTEXT>> callback) {
    actorTaskSchedulingService.submit(() -> startupSynchronized(context, callback));
  }

  private void startupSynchronized(
      final CONTEXT context, final Consumer<Either<Throwable, CONTEXT>> callback) {
    logger.debug("Startup was called with context: " + context);
    if (startupCalled) {
      throw new IllegalStateException("startup(...) must only be called once");
    }
    startupCalled = true;

    // when startup is complete
    startupCallbackAggregate = new AggregateConsumer<>();
    // call the original consumer
    startupCallbackAggregate.addConsumer(callback);
    /* and reset the callback in this class; the presence of the field is an indicator for the
    shutdown process that startup has not completed yet*/
    startupCallbackAggregate.addConsumer(
        (either) -> actorTaskSchedulingService.submit(() -> startupCallbackAggregate = null));

    final var stepsToStart = new ArrayDeque<>(steps);

    logger.info("Starting startup process");
    proceedWithStartupSynchronized(stepsToStart, context, startupCallbackAggregate);
  }

  private void proceedWithStartupSynchronized(
      final Queue<StartupStep<CONTEXT>> stepsToStart,
      final CONTEXT context,
      final Consumer<Either<Throwable, CONTEXT>> callback) {
    if (shutdownCalled) {
      logger.info("Aborting startup process because shutdown was called");
      callback.accept(
          Either.left(
              new CancellationException("Aborting startup process because shutdown was called")));
    } else if (stepsToStart.isEmpty()) {
      callback.accept(Either.right(context));
      logger.info("Finished startup process");
    } else {
      final var stepToStart = stepsToStart.poll();
      startedSteps.push(stepToStart);

      logCurrentStep("Startup", stepToStart);
      stepToStart.startup(
          context,
          (either) ->
              actorTaskSchedulingService.submit(
                  () ->
                      onStartupStepCompletedSynchronized(
                          either, stepsToStart, stepToStart, callback)));
    }
  }

  private void onStartupStepCompletedSynchronized(
      final Either<Throwable, CONTEXT> result,
      final Queue<StartupStep<CONTEXT>> stepsToStart,
      final StartupStep<CONTEXT> startedStep,
      final Consumer<Either<Throwable, CONTEXT>> callback) {
    result.ifRightOrLeft(
        context -> proceedWithStartupSynchronized(stepsToStart, context, callback),
        error -> {
          logger.warn(
              "Aborting startup process due to exception during stage " + startedStep.getName(),
              error);
          callback.accept(Either.left(error));
        });
  }

  private void proceedWithShutdownSynchronized(
      final CONTEXT context,
      final Consumer<Either<Throwable, CONTEXT>> callback,
      final List<ShutdownExceptionRecord> collectedExceptions) {
    if (startedSteps.isEmpty()) {
      completeShutdownFutureSynchronized(context, callback, collectedExceptions);
    } else {
      final var stepToShutdown = startedSteps.pop();

      logCurrentStep("Shutdown", stepToShutdown);
      stepToShutdown.shutdown(
          context,
          (either) ->
              actorTaskSchedulingService.submit(
                  () ->
                      onShutdownStepCompletedSynchronized(
                          context, either, stepToShutdown, callback, collectedExceptions)));
    }
  }

  private void onShutdownStepCompletedSynchronized(
      final CONTEXT lastShutdownContext,
      final Either<Throwable, CONTEXT> result,
      final StartupStep<CONTEXT> currentStep,
      final Consumer<Either<Throwable, CONTEXT>> callback,
      final List<ShutdownExceptionRecord> collectedExceptions) {
    result.ifRightOrLeft(
        context -> proceedWithShutdownSynchronized(context, callback, collectedExceptions),
        error -> {
          collectedExceptions.add(new ShutdownExceptionRecord(currentStep, error));

          logger.warn(
              "Aborting startup process due to exception during stage " + currentStep.getName(),
              error);
          proceedWithShutdownSynchronized(lastShutdownContext, callback, collectedExceptions);
        });
  }

  private void completeShutdownFutureSynchronized(
      final CONTEXT context,
      final Consumer<Either<Throwable, CONTEXT>> callback,
      final List<ShutdownExceptionRecord> collectedExceptions) {
    if (collectedExceptions.isEmpty()) {
      callback.accept(Either.right(context));
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
      callback.accept(Either.left(exception));
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

  private static final class AggregateConsumer<TYPE> implements Consumer<TYPE> {

    private final Set<Consumer<TYPE>> delegates = new HashSet<>();
    private TYPE capturedResult;

    @Override
    public void accept(final TYPE result) {
      delegates.forEach(consumer -> consumer.accept(result));

      capturedResult = result;
    }

    private void addConsumer(final Consumer<TYPE> consumer) {
      delegates.add(consumer);

      if (capturedResult != null) {
        consumer.accept(capturedResult);
      }
    }
  }
}
