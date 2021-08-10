/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.startup;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.ActorTaskSchedulingService;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings({"unchecked"})
// TODO (probably impossible) test whether the task scheduler is called at the right time with the
// right values
class StartupProcessTest {

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

  private static final ActorTaskSchedulingService TEST_SCHEDULING_SERVICE =
      new TestActorTaskSchedulingService();

  static final class TestActorTaskSchedulingService implements ActorTaskSchedulingService {

    @Override
    public void submit(final Runnable action) {
      action.run();
    }
  }

  static class NoopStartupStep implements StartupStep<Object> {

    @Override
    public String getName() {
      return "NoopStartupStep";
    }

    @Override
    public void startup(final Object o, final Consumer<Either<Throwable, Object>> callback) {
      callback.accept(Either.right(o));
    }

    @Override
    public void shutdown(final Object o, final Consumer<Either<Throwable, Object>> callback) {
      callback.accept(Either.right(o));
    }
  }

  static class TestStartupStep implements StartupStep<Object> {

    private final Object expectedStartupContext;
    private final Either<Throwable, Object> resultOfStartupContext;
    private final Object expectedShutdownContext;
    private final Either<Throwable, Object> resultOfShutdownContext;

    TestStartupStep(
        final Object expectedStartupContext,
        final Either<Throwable, Object> resultOfStartupContext,
        final Object expectedShutdownContext,
        final Either<Throwable, Object> resultOfShutdownContext) {
      this.expectedStartupContext = expectedStartupContext;
      this.resultOfStartupContext = resultOfStartupContext;
      this.expectedShutdownContext = expectedShutdownContext;
      this.resultOfShutdownContext = resultOfShutdownContext;
    }

    @Override
    public String getName() {
      return "NoopStartupStep";
    }

    @Override
    public void startup(final Object o, final Consumer<Either<Throwable, Object>> callback) {
      if (expectedStartupContext.equals(o)) {
        callback.accept(resultOfStartupContext);
      } else {
        throw new IllegalArgumentException(
            "Expected input to be" + expectedStartupContext + " but was called with " + o);
      }
    }

    @Override
    public void shutdown(final Object o, final Consumer<Either<Throwable, Object>> callback) {
      if (expectedShutdownContext.equals(o)) {
        callback.accept(resultOfShutdownContext);
      } else {
        throw new IllegalArgumentException(
            "Expected input to be" + expectedShutdownContext + " but was called with " + o);
      }
    }
  }

  static final class InvocationCountingStartupStep implements StartupStep<Object> {

    private int startupInvocationCounter = 0;
    private int shutdownInvocationCounter = 0;

    int getStartupInvocationCounter() {
      return startupInvocationCounter;
    }

    int getShutdownInvocationCounter() {
      return shutdownInvocationCounter;
    }

    @Override
    public String getName() {
      return "InvocationCountingStartupStep";
    }

    @Override
    public void startup(final Object o, final Consumer<Either<Throwable, Object>> callback) {
      startupInvocationCounter++;
      callback.accept(Either.right(o));
    }

    @Override
    public void shutdown(final Object o, final Consumer<Either<Throwable, Object>> callback) {
      shutdownInvocationCounter++;
      callback.accept(Either.right(o));
    }
  }

  static final class WaitingStartupStep implements StartupStep<Object> {

    private final CountDownLatch startupCountdownLatch;
    private final boolean completeWithException;

    WaitingStartupStep(
        final CountDownLatch startupCountdownLatch, final boolean completeWithException) {
      this.startupCountdownLatch = startupCountdownLatch;
      this.completeWithException = completeWithException;
    }

    @Override
    public String getName() {
      return "WaitingStartupStep";
    }

    @Override
    public void startup(final Object o, final Consumer<Either<Throwable, Object>> callback) {

      final var startupThread =
          new Thread(
              () -> {
                try {
                  startupCountdownLatch.await();
                } catch (final InterruptedException e) {
                  e.printStackTrace();
                } finally {
                  if (!completeWithException) {
                    callback.accept(Either.right(o));
                  } else {
                    callback.accept(Either.left(new Throwable("completed exceptionally")));
                  }
                }
              });
      startupThread.start();
    }

    @Override
    public void shutdown(final Object o, final Consumer<Either<Throwable, Object>> callback) {
      callback.accept(Either.right(o));
    }
  }

  @Nested
  class MainUseCase {

    private static final String INPUT_STEP1 = "inputStep1";
    private static final String INPUT_STEP2 = "inputStep2";
    private static final String RESULT_STEP1 = "resultStep1";
    private static final String RESULT_STEP2 = "resultStep2";

    private final Exception testException1 = new Exception("TEST_EXCEPTION1");
    private final Exception testException2 = new Exception("TEST_EXCEPTION1");

    private StartupStep<Object> spyStep1;
    private StartupStep<Object> spyStep2;

    @BeforeEach
    void setup() {
      final var step1 = new NoopStartupStep();
      final var step2 = new NoopStartupStep();

      spyStep1 = spy(step1);
      spyStep2 = spy(step2);

      when(spyStep1.getName()).thenReturn("step1");
      when(spyStep2.getName()).thenReturn("step2");
    }

    @Test
    void shouldCallStartupStepsInOrder() {
      // given
      final var sut = new StartupProcess<>(TEST_SCHEDULING_SERVICE, List.of(spyStep1, spyStep2));

      // when
      sut.startup(STARTUP_CONTEXT, (result) -> {});

      // then
      final var invocationRecorder = inOrder(spyStep1, spyStep2);
      invocationRecorder.verify(spyStep1).startup(eq(STARTUP_CONTEXT), any());
      invocationRecorder.verify(spyStep2).startup(eq(STARTUP_CONTEXT), any());
    }

    @Test
    void shouldCallShutdownStepsInReverseOrder() {
      // given
      final var sut = new StartupProcess<>(TEST_SCHEDULING_SERVICE, List.of(spyStep1, spyStep2));
      sut.startup(STARTUP_CONTEXT, (result) -> {});

      // when
      sut.shutdown(SHUTDOWN_CONTEXT, (result) -> {});

      // then
      final var invocationRecorder = inOrder(spyStep1, spyStep2);
      invocationRecorder.verify(spyStep2).shutdown(eq(SHUTDOWN_CONTEXT), any());
      invocationRecorder.verify(spyStep1).shutdown(eq(SHUTDOWN_CONTEXT), any());
    }

    @Test
    void shouldCallSubsequentStartupStepWithResultOfPreviousStep() {
      // given
      final var step1 = new TestStartupStep(INPUT_STEP1, Either.right(RESULT_STEP1), null, null);
      final var step2 = new TestStartupStep(RESULT_STEP1, Either.right(RESULT_STEP2), null, null);

      spyStep1 = spy(step1);
      spyStep2 = spy(step2);

      when(spyStep1.getName()).thenReturn("step1");
      when(spyStep2.getName()).thenReturn("step2");

      final var sut = new StartupProcess<>(TEST_SCHEDULING_SERVICE, List.of(spyStep1, spyStep2));

      final var mockCallback = Mockito.mock(Consumer.class);

      // when
      sut.startup(INPUT_STEP1, mockCallback);

      // then
      final var invocationRecorder = inOrder(spyStep1, spyStep2);
      invocationRecorder.verify(spyStep1).startup(eq(INPUT_STEP1), any());
      invocationRecorder.verify(spyStep2).startup(eq(RESULT_STEP1), any());

      final var argumentCaptor = ArgumentCaptor.forClass(Either.class);
      verify(mockCallback).accept(argumentCaptor.capture());

      assertThat(argumentCaptor.getValue().get()).isSameAs(RESULT_STEP2);
    }

    @Test
    void shouldCallSubsequentShutdownStepWithResultOfPreviousStep() {
      // given
      final var step1 =
          new TestStartupStep(
              INPUT_STEP1, Either.right(RESULT_STEP1), RESULT_STEP2, Either.right(RESULT_STEP1));
      final var step2 =
          new TestStartupStep(
              RESULT_STEP1, Either.right(RESULT_STEP2), INPUT_STEP2, Either.right(RESULT_STEP2));

      spyStep1 = spy(step1);
      spyStep2 = spy(step2);

      when(spyStep1.getName()).thenReturn("step1");
      when(spyStep2.getName()).thenReturn("step2");

      final var sut = new StartupProcess<>(TEST_SCHEDULING_SERVICE, List.of(spyStep1, spyStep2));

      final var mockCallback = Mockito.mock(Consumer.class);

      sut.startup(INPUT_STEP1, (result) -> {});

      // when
      sut.shutdown(INPUT_STEP2, mockCallback);

      // then
      final var invocationRecorder = inOrder(spyStep1, spyStep2);
      invocationRecorder.verify(spyStep2).shutdown(eq(INPUT_STEP2), any());
      invocationRecorder.verify(spyStep1).shutdown(eq(RESULT_STEP2), any());

      final var argumentCaptor = ArgumentCaptor.forClass(Either.class);
      verify(mockCallback).accept(argumentCaptor.capture());

      assertThat(argumentCaptor.getValue().get()).isSameAs(RESULT_STEP1);
    }

    @Test
    void shouldAbortStartupIfOneStepThrewAnException() {
      // given
      final var testException = new Exception("TEST_EXCEPTION");

      final var step1 =
          new TestStartupStep(
              STARTUP_CONTEXT,
              Either.left(testException),
              RESULT_STEP2,
              Either.right(RESULT_STEP1));

      spyStep1 = spy(step1);
      when(spyStep1.getName()).thenReturn("step1");

      final var sut = new StartupProcess<>(TEST_SCHEDULING_SERVICE, List.of(spyStep1, spyStep2));

      final var mockCallback = Mockito.mock(Consumer.class);

      // when
      sut.startup(STARTUP_CONTEXT, mockCallback);

      // then
      verify(spyStep2, never()).startup(eq(STARTUP_CONTEXT), any());

      final var argumentCaptor = ArgumentCaptor.forClass(Either.class);
      verify(mockCallback).accept(argumentCaptor.capture());

      assertThat(argumentCaptor.getValue().getLeft()).isSameAs(testException);
    }

    @Test
    void shouldContinueShutdownEvenIfStepsThrowExceptions() {
      // given
      final var step1 =
          new TestStartupStep(
              STARTUP_CONTEXT,
              Either.right(STARTUP_CONTEXT),
              SHUTDOWN_CONTEXT,
              Either.left(testException1));
      final var step2 =
          new TestStartupStep(
              STARTUP_CONTEXT,
              Either.right(STARTUP_CONTEXT),
              SHUTDOWN_CONTEXT,
              Either.left(testException2));

      spyStep1 = spy(step1);
      spyStep2 = spy(step2);

      when(spyStep1.getName()).thenReturn("step1");
      when(spyStep2.getName()).thenReturn("step2");

      final var sut = new StartupProcess<>(TEST_SCHEDULING_SERVICE, List.of(spyStep1, spyStep2));

      sut.startup(STARTUP_CONTEXT, (result) -> {});

      final var mockCallback = Mockito.mock(Consumer.class);

      // when
      sut.shutdown(SHUTDOWN_CONTEXT, mockCallback);

      // then
      verify(spyStep2).shutdown(eq(SHUTDOWN_CONTEXT), any());
      verify(spyStep1).shutdown(eq(SHUTDOWN_CONTEXT), any());

      final var argumentCaptor = ArgumentCaptor.forClass(Either.class);
      verify(mockCallback).accept(argumentCaptor.capture());

      assertThat(argumentCaptor.getValue().getLeft()).isInstanceOf(Exception.class);

      final var exception = (Throwable) argumentCaptor.getValue().getLeft();

      assertThat(exception)
          .hasSuppressedException(testException1)
          .hasSuppressedException(testException2);
    }

    @Test
    void shouldAbortOngoingStartupWhenShutdownIsCalled() {
      // given
      final var step1CountdownLatch = new CountDownLatch(1);
      final var step1 = new WaitingStartupStep(step1CountdownLatch, false);

      final var sut = new StartupProcess<>(TEST_SCHEDULING_SERVICE, List.of(step1, spyStep2));

      final var mockStartupCallback = Mockito.mock(Consumer.class);
      final var mockShutdownCallback = Mockito.mock(Consumer.class);

      // when
      sut.startup(STARTUP_CONTEXT, mockStartupCallback);
      sut.shutdown(SHUTDOWN_CONTEXT, mockShutdownCallback);

      step1CountdownLatch.countDown();

      // then
      verifyNoInteractions(spyStep2);

      final var startupArgumentCaptor = ArgumentCaptor.forClass(Either.class);
      verify(mockStartupCallback, timeout(100)).accept(startupArgumentCaptor.capture());

      assertThat(startupArgumentCaptor.getValue().isLeft())
          .describedAs("Startup completed with exception")
          .isTrue();

      final var shutdownArgumentCaptor = ArgumentCaptor.forClass(Either.class);
      verify(mockShutdownCallback).accept(shutdownArgumentCaptor.capture());

      assertThat(shutdownArgumentCaptor.getValue().get()).isSameAs(SHUTDOWN_CONTEXT);
    }
  }

  @Nested
  class IllegalStatesAndArguments {

    @Test
    void shouldThrowNPEWhenCalledWithNoSteps() {
      // when + then
      assertThatThrownBy(() -> new StartupProcess<>(TEST_SCHEDULING_SERVICE, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNPEWhenCalledWithNoScheduler() {
      // when + then
      assertThatThrownBy(() -> new StartupProcess<>(null, Collections.emptyList()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNPEWhenCalledWithNoLogger() {
      // when + then
      assertThatThrownBy(
              () -> new StartupProcess<>(null, TEST_SCHEDULING_SERVICE, Collections.emptyList()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowIllegalStateIfStartupIsCalledMoreThanOnce() {
      // given
      final var sut = new StartupProcess<>(TEST_SCHEDULING_SERVICE, Collections.emptyList());

      // when + then
      sut.startup(STARTUP_CONTEXT, (result) -> {});

      assertThatThrownBy(() -> sut.startup(STARTUP_CONTEXT, (result) -> {}))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("startup(...) must only be called once");
    }

    @Test
    void shouldPerformShutdownOnlyOnceIfShutdownIsCalledMultipleTimes() {
      // given
      final var step = new InvocationCountingStartupStep();
      final var sut = new StartupProcess<>(TEST_SCHEDULING_SERVICE, singletonList(step));

      // when
      sut.startup(STARTUP_CONTEXT, (result) -> {});
      sut.shutdown(SHUTDOWN_CONTEXT, (result) -> {});
      sut.shutdown(SHUTDOWN_CONTEXT, (result) -> {});

      // then
      assertThat(step.getShutdownInvocationCounter()).isEqualTo(1);
    }
  }

  @Nested
  class EmptyList {

    private final StartupProcess<Object> sut =
        new StartupProcess<>(TEST_SCHEDULING_SERVICE, Collections.emptyList());

    @Test
    void shouldReturnContextImmediatelyOnStartup() {
      final var mockCallback = Mockito.mock(Consumer.class);

      // when
      sut.startup(STARTUP_CONTEXT, mockCallback);

      // then
      final var argumentCaptor = ArgumentCaptor.forClass(Either.class);
      verify(mockCallback).accept(argumentCaptor.capture());

      assertThat(argumentCaptor.getValue().get()).isSameAs(STARTUP_CONTEXT);
    }

    @Test
    void shouldReturnContextImmediatelyOnShutdown() {
      // given
      sut.startup(STARTUP_CONTEXT, (result) -> {});

      final var mockCallback = Mockito.mock(Consumer.class);

      // when
      sut.shutdown(SHUTDOWN_CONTEXT, mockCallback);

      // then
      final var argumentCaptor = ArgumentCaptor.forClass(Either.class);
      verify(mockCallback).accept(argumentCaptor.capture());

      assertThat(argumentCaptor.getValue().get()).isSameAs(SHUTDOWN_CONTEXT);
    }
  }
}
