/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

public class AbstractStartupStepTest {

  private static final Object STARTUP_CONTEXT =
      new Object() {
        @Override
        public String toString() {
          return "startupContext";
        }
      };

  private static final Object SHUTDOWN_CONTEXT =
      new Object() {
        @Override
        public String toString() {
          return "shutdownContext";
        }
      };

  @Test
  public void shouldThrowIllegalStateExceptionWhenShutdownIsCalledBeforeStartup() {
    // given
    final var sut = new AutomaticStartupStep();

    // when + then
    assertThatThrownBy(() -> sut.shutdown(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("shutdown(...) can only be called after startup(...)");
  }

  @Test
  public void shouldThrowIllegalStateIfStartupIsCalledMoreThanOnce() {
    // given
    final var sut = new AutomaticStartupStep();

    // when + then
    sut.startup(null).join();

    assertThatThrownBy(() -> sut.startup(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("startup(...) must only be called once");
  }

  @Test
  public void shouldPerformShutdownOnlyOnceIfShutdownIsCalledMultipleTimes() {
    // given
    final var sut = new InvocationCountingStartupStep();

    // when
    sut.startup(null).join();
    sut.shutdown(null).join();
    sut.shutdown(null).join();

    // then
    assertThat(sut.getShutdownInvocationCounter()).isEqualTo(1);
  }

  @Test
  public void shouldCompleteWithIllegalStateExceptionIfImplementationReturnsNullFromStartup() {
    // given
    final var sut = new SabotagedStartupStep(true, false);

    // when
    final var startupFuture = sut.startup(null);

    // then
    assertThatThrownBy(startupFuture::get)
        .isInstanceOf(ExecutionException.class)
        .getCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("startupGuarded(...) did return null, instead of a future object");
  }

  @Test
  public void shouldCompleteWithIllegalStateExceptionIfImplementationReturnsNullFromShutdown() {
    // given
    final var sut = new SabotagedStartupStep(false, true);

    // when
    sut.startup(null).join();
    final var shutdownFuture = sut.shutdown(null);

    // then
    assertThatThrownBy(shutdownFuture::get)
        .isInstanceOf(ExecutionException.class)
        .getCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("shutdownGuarded(...) did return null, instead of a future object");
  }

  @Test
  public void
      shouldPerformShutdownImmediatelyAfterStartupCompletedSuccessfullyIfStartupIsStillRunning()
          throws ExecutionException, InterruptedException, TimeoutException {
    // given
    final var startupCountdownLatch = new CountDownLatch(1);

    final var sut = new WaitingStartupStep(startupCountdownLatch, false);

    // when
    final var startupFuture = sut.startup(STARTUP_CONTEXT);
    final var shutdownFuture = sut.shutdown(SHUTDOWN_CONTEXT);

    // then
    assertThat(startupFuture).isNotDone();
    assertThat(shutdownFuture).isNotDone();

    // when
    startupCountdownLatch.countDown();
    startupFuture.get(100, TimeUnit.MILLISECONDS);
    shutdownFuture.get(100, TimeUnit.MILLISECONDS);

    // then
    assertThat(startupFuture).isCompletedWithValue(STARTUP_CONTEXT);
    /* In case the startup was still running when shutdown was called, and the startup
     * completed successfully, the shutdown shall be called with the startup context returned
     * by the startup process, because that context is believed to be better (more recent, more accurate)
     * than the context that was used when calling shutdown
     */
    assertThat(shutdownFuture).isCompletedWithValue(STARTUP_CONTEXT);
  }

  @Test
  public void
      shouldPerformShutdownImmediatelyAfterStartupCompletedExceptionallyIfStartupIsStillRunning()
          throws ExecutionException, InterruptedException, TimeoutException {
    // given
    final var startupCountdownLatch = new CountDownLatch(1);
    final var sut = new WaitingStartupStep(startupCountdownLatch, true);

    // when
    final var startupFuture = sut.startup(STARTUP_CONTEXT);
    final var shutdownFuture = sut.shutdown(SHUTDOWN_CONTEXT);

    // then
    assertThat(startupFuture).isNotDone();
    assertThat(shutdownFuture).isNotDone();

    // when
    startupCountdownLatch.countDown();
    try {
      startupFuture.get(100, TimeUnit.MILLISECONDS);
    } catch (final Throwable t) {
      // expected exception
    }
    shutdownFuture.get(100, TimeUnit.MILLISECONDS);

    // then
    assertThat(startupFuture).isCompletedExceptionally();
    /* In case the startup was still running when shutdown was called, and the startup
     * completed with en error, the shutdown shall be called with the shutdown context that
     * was passed to the shutdown method, because that context is believed to be better (i.e. the
     * last consistent state before the error occurred)
     */
    assertThat(shutdownFuture).isCompletedWithValue(SHUTDOWN_CONTEXT);
  }

  public static final class InvocationCountingStartupStep extends AbstractStartupStep<Object> {

    private int startupInvocationCounter = 0;
    private int shutdownInvocationCounter = 0;

    @Override
    protected CompletableFuture<Object> startupGuarded(final Object o) {
      startupInvocationCounter++;
      return CompletableFuture.completedFuture(o);
    }

    @Override
    protected CompletableFuture<Object> shutdownGuarded(final Object o) {
      shutdownInvocationCounter++;
      return CompletableFuture.completedFuture(o);
    }

    public int getStartupInvocationCounter() {
      return startupInvocationCounter;
    }

    public int getShutdownInvocationCounter() {
      return shutdownInvocationCounter;
    }

    @Override
    public String getName() {
      return "InvocationCountingStartupStep";
    }
  }

  static final class WaitingStartupStep extends AbstractStartupStep<Object> {

    private final CountDownLatch startupCountdownLatch;
    private final boolean completeWithException;

    WaitingStartupStep(
        final CountDownLatch startupCountdownLatch, final boolean completeWithException) {
      this.startupCountdownLatch = startupCountdownLatch;
      this.completeWithException = completeWithException;
    }

    @Override
    protected CompletableFuture<Object> startupGuarded(final Object o) {
      final var startupFuture = new CompletableFuture<>();
      final var startupThread =
          new Thread(
              () -> {
                try {
                  startupCountdownLatch.await();
                } catch (final InterruptedException e) {
                  e.printStackTrace();
                } finally {
                  if (!completeWithException) {
                    startupFuture.complete(o);
                  } else {
                    startupFuture.completeExceptionally(new Throwable("completed exceptionally"));
                  }
                }
              });
      startupThread.start();
      return startupFuture;
    }

    @Override
    protected CompletableFuture<Object> shutdownGuarded(final Object o) {
      return CompletableFuture.completedFuture(o);
    }

    @Override
    public String getName() {
      return "WaitingStartupStep";
    }
  }

  private static final class AutomaticStartupStep extends AbstractStartupStep<Object> {

    @Override
    protected CompletableFuture<Object> startupGuarded(final Object o) {
      return CompletableFuture.completedFuture(o);
    }

    @Override
    protected CompletableFuture<Object> shutdownGuarded(final Object o) {
      return CompletableFuture.completedFuture(o);
    }

    @Override
    public String getName() {
      return "AutomaticStartupStep";
    }
  }

  private static final class SabotagedStartupStep extends AbstractStartupStep<Object> {

    private final boolean sabotageStartup;
    private final boolean sabotageSShutdown;

    private SabotagedStartupStep(final boolean sabotageStartup, final boolean sabotageSShutdown) {
      this.sabotageStartup = sabotageStartup;
      this.sabotageSShutdown = sabotageSShutdown;
    }

    @Override
    protected CompletableFuture<Object> startupGuarded(final Object o) {
      return sabotageStartup ? null : CompletableFuture.completedFuture(o);
    }

    @Override
    protected CompletableFuture<Object> shutdownGuarded(final Object o) {
      return sabotageSShutdown ? null : CompletableFuture.completedFuture(o);
    }

    @Override
    public String getName() {
      return "UselessStartupStep";
    }
  }
}
