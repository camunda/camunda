package io.camunda.zeebe.util.startup.actor;

import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import org.slf4j.Logger;

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
 *   <li>An actor <em>must</em> own the instance of {@link BootstrapProcess}
 *   <li>{@link #startup(Object)} MUST be called from that actor's context
 *   <li>{@link #shutdown(Object)} MUST be called from that actor's context
 *   <li>{@link #shutdown(Object)} must not be called before startup
 *   <li>@link #startup(Object)} must be called at most once
 *   <li>{@link #shutdown(Object)} may be called more than once. The first call will trigger the
 *       shutdown and any subsequent calls do nothing
 *   <li>{@link #shutdown(Object)} may be called before the future of startup has completed. In that
 *       case, it will complete the current running startup step, cancel all subsequent startup
 *       step, complete the startup future with an exception and start the shutdown from the step
 *       that last completed
 * </ul>
 *
 * @param <CONTEXT> the startup/shutdown context
 */
public final class BootstrapProcess<CONTEXT> {
  private final String name;
  private final Logger logger;
  private final Queue<BootstrapStep<CONTEXT>> steps;

  private final Deque<BootstrapStep<CONTEXT>> startedSteps = new ArrayDeque<>();
  private final CompletableActorFuture<CONTEXT> startupFuture = new CompletableActorFuture<>();
  private final CompletableActorFuture<CONTEXT> shutdownFuture = new CompletableActorFuture<>();

  private boolean startupRequested = false;
  private boolean shutdownRequested = false;

  public BootstrapProcess(
      final String name, final List<BootstrapStep<CONTEXT>> steps, final Logger logger) {
    this.name = name;
    // copy the steps to avoid mutating the given list
    this.steps = new ArrayDeque<>(steps);
    this.logger = logger;
  }

  /** Must be called from within the host actor context. */
  public ActorFuture<CONTEXT> startup(final CONTEXT context) {
    if (shutdownRequested) {
      throw new IllegalStateException(
          String.format("Shutdown of bootstrap process %s was already requested", name));
    }

    if (startupRequested) {
      throw new IllegalStateException(
          String.format(
              "Startup of bootstrap process %s should only be requested once, and was already requested",
              name));
    }

    if (logger.isInfoEnabled()) {
      logger.info("{} - Bootstrap startup started with {} steps", name, steps.size());
    } else if (logger.isDebugEnabled()) {
      final var stepNames = steps.stream().map(BootstrapStep::getName).collect(Collectors.toList());
      logger.debug(
          "{} - Bootstrap startup started with #{} steps: {}", name, steps.size(), stepNames);
    }

    startupRequested = true;
    proceedWithStartup(context);
    return startupFuture;
  }

  /** Must be called from within the host actor context. */
  public ActorFuture<CONTEXT> shutdown(final CONTEXT context) {
    if (shutdownRequested) {
      logger.debug("{} - Shutdown already in progress", name);
      return shutdownFuture;
    }

    if (logger.isInfoEnabled()) {
      logger.info("{} - Bootstrap shutdown started with {} steps", name, startedSteps.size());
    } else if (logger.isDebugEnabled()) {
      final var stepNames =
          startedSteps.stream().map(BootstrapStep::getName).collect(Collectors.toList());
      logger.debug(
          "{} - Bootstrap shutdown started with #{} steps: {}",
          name,
          startedSteps.size(),
          stepNames);
    }

    // signal that shutdown was called; this is read by the startup process to abort a concurrent
    // startup process
    shutdownRequested = true;

    if (startupRequested) {
      // wait until the current startup step finishes and then complete it
      // TODO: how to propagate the fact that startup was cancelled?
      startupFuture.onComplete(
          (latestContext, error) -> {
            // if all startup steps were completed, then error is null and we can use ctxt
            // if there was an error in the last step, then error is not null and we should use the
            //  shutdown context
            final CONTEXT contextToUse = error == null ? latestContext : context;
            proceedWithShutdown(contextToUse, new ArrayList<>());
          });
    } else {
      proceedWithShutdown(context, new ArrayList<>());
    }

    return shutdownFuture;
  }

  private void proceedWithStartup(final CONTEXT context) {
    if (shutdownRequested) {
      logger.info("{} - Interrupting startup process due to shutdown request", name);
      startupFuture.complete(context);
      return;
    }

    if (steps.isEmpty()) {
      startupFuture.complete(context);
      return;
    }

    final var currentStep = steps.poll();
    // TODO: should we push it as started before or after it finishes?
    startedSteps.push(currentStep);

    logger.debug("{} - Proceeding to next startup step {}", name, currentStep.getName());
    logger.trace(
        "{} - Invoking startup of step {} with context {}", name, currentStep.getName(), context);
    currentStep
        .startup(context)
        .onComplete((newContext, error) -> onCompleteStartupStep(currentStep, newContext, error));
  }

  private void onCompleteStartupStep(
      final BootstrapStep<CONTEXT> currentStep, final CONTEXT newContext, final Throwable error) {
    if (error == null) {
      proceedWithStartup(newContext);
      return;
    }

    logger.warn(
        "{} - Aborting startup process due to exception during stage {}",
        name,
        currentStep.getName());
    startupFuture.completeExceptionally(error);
  }

  private void proceedWithShutdown(
      final CONTEXT context, final List<BootstrapStepException> exceptions) {

    if (startedSteps.isEmpty()) {
      if (exceptions.isEmpty()) {
        shutdownFuture.complete(context);
      } else {
        shutdownFuture.completeExceptionally(aggregateExceptions(exceptions));
      }

      return;
    }

    final var currentStep = startedSteps.pop();
    logger.debug("{} - Proceeding to next shutdown step {}", name, currentStep.getName());
    logger.trace(
        "{} - Invoking shutdown of step {} with context {}", name, currentStep.getName(), context);
    currentStep
        .shutdown(context)
        .onComplete(
            (newContext, error) ->
                onCompleteShutdownStep(context, exceptions, currentStep, newContext, error));
  }

  private void onCompleteShutdownStep(
      final CONTEXT context,
      final List<BootstrapStepException> exceptions,
      final BootstrapStep<CONTEXT> currentStep,
      final CONTEXT newContext,
      final Throwable error) {
    if (error == null) {
      proceedWithShutdown(newContext, exceptions);
      return;
    }

    exceptions.add(new BootstrapStepException(error, currentStep.getName()));
    logger.warn(
        "Aborting startup process due to exception during stage " + currentStep.getName(), error);
    proceedWithShutdown(context, exceptions);
  }

  private Throwable aggregateExceptions(final List<BootstrapStepException> exceptions) {
    final var failedSteps =
        exceptions.stream().map(BootstrapStepException::getStepName).collect(Collectors.toList());
    final var message =
        String.format(
            "Bootstrap %s failed in the following steps: %s. See suppressed exceptions for details.",
            name, failedSteps);

    final var exception = new BootstrapException(message);
    exceptions.forEach(exception::addSuppressed);
    return exception;
  }
}
