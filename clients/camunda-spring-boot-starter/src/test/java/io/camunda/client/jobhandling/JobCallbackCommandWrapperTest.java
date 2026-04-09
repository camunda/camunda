/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.jobhandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.JobCallbackFinalCommandStep;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
public class JobCallbackCommandWrapperTest {

  private JobCallbackFinalCommandStep<Object> command;
  private MetricsRecorder metricsRecorder;
  private CounterMetricsContext metricsContext;

  @BeforeEach
  void setUp() {
    command = mock(JobCallbackFinalCommandStep.class);
    metricsRecorder = new DefaultNoopMetricsRecorder();
    metricsContext = mock(CounterMetricsContext.class);
  }

  @Test
  void immediateSuccessCompletesWithCompleted() {
    final CompletableFuture<Object> sendFuture = new CompletableFuture<>();
    when(command.send()).thenReturn(asCamundaFuture(sendFuture));

    final JobCallbackCommandExceptionHandlingStrategy strategy =
        mock(JobCallbackCommandExceptionHandlingStrategy.class);
    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 3);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();

    assertThat(result).isNotDone();

    final Object response = new Object();
    sendFuture.complete(response);

    assertThat(result).isDone();
    final CommandOutcome outcome = result.join();
    assertThat(outcome).isInstanceOf(CommandOutcome.Completed.class);
    final CommandOutcome.Completed completed = (CommandOutcome.Completed) outcome;
    assertThat(completed.response()).isSameAs(response);
    assertThat(completed.attempts()).isEqualTo(1);
  }

  @Test
  void immediateNonRetriableFailureCompletesWithFailed() {
    final CompletableFuture<Object> sendFuture = new CompletableFuture<>();
    when(command.send()).thenReturn(asCamundaFuture(sendFuture));

    final JobCallbackCommandExceptionHandlingStrategy strategy =
        mock(JobCallbackCommandExceptionHandlingStrategy.class);
    final StatusRuntimeException error = new StatusRuntimeException(Status.INTERNAL);
    when(strategy.handleCommandError(any(), any())).thenReturn(new CommandOutcome.Failed(error, 1));

    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 3);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();
    sendFuture.completeExceptionally(error);

    assertThat(result).isDone();
    final CommandOutcome outcome = result.join();
    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    final CommandOutcome.Failed failed = (CommandOutcome.Failed) outcome;
    assertThat(failed.cause()).isSameAs(error);
  }

  @Test
  void notFoundCompletesWithIgnored() {
    final CompletableFuture<Object> sendFuture = new CompletableFuture<>();
    when(command.send()).thenReturn(asCamundaFuture(sendFuture));

    final JobCallbackCommandExceptionHandlingStrategy strategy =
        mock(JobCallbackCommandExceptionHandlingStrategy.class);
    final StatusRuntimeException error = new StatusRuntimeException(Status.NOT_FOUND);
    when(strategy.handleCommandError(any(), any()))
        .thenReturn(new CommandOutcome.Ignored(error, 1));

    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 3);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();
    sendFuture.completeExceptionally(error);

    assertThat(result).isDone();
    final CommandOutcome outcome = result.join();
    assertThat(outcome).isInstanceOf(CommandOutcome.Ignored.class);
    final CommandOutcome.Ignored ignored = (CommandOutcome.Ignored) outcome;
    assertThat(ignored.cause()).isSameAs(error);
  }

  @Test
  void retryThenSuccessCompletesWithCompleted() {
    final CompletableFuture<Object> firstSend = new CompletableFuture<>();
    final CompletableFuture<Object> secondSend = new CompletableFuture<>();
    when(command.send())
        .thenReturn(asCamundaFuture(firstSend))
        .thenReturn(asCamundaFuture(secondSend));

    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final BackoffSupplier backoff = BackoffSupplier.newBackoffBuilder().build();
    final JobCallbackCommandExceptionHandlingStrategy strategy =
        new JobCallbackCommandExceptionHandlingStrategy(backoff, executor);

    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 3);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();

    // First send fails with retriable error
    firstSend.completeExceptionally(new StatusRuntimeException(Status.UNAVAILABLE));

    // Future should still be pending (retry scheduled)
    assertThat(result).isNotDone();

    // Capture and execute the scheduled retry
    final ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).schedule(runnableCaptor.capture(), any(Long.class), any(TimeUnit.class));
    runnableCaptor.getValue().run();

    // Second send succeeds
    final Object response = new Object();
    secondSend.complete(response);

    assertThat(result).isDone();
    final CommandOutcome outcome = result.join();
    assertThat(outcome).isInstanceOf(CommandOutcome.Completed.class);
    final CommandOutcome.Completed completed = (CommandOutcome.Completed) outcome;
    assertThat(completed.response()).isSameAs(response);
    assertThat(completed.attempts()).isEqualTo(2);
  }

  @Test
  void retryExhaustionCompletesWithFailed() {
    final CompletableFuture<Object> firstSend = new CompletableFuture<>();
    when(command.send()).thenReturn(asCamundaFuture(firstSend));

    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final BackoffSupplier backoff = BackoffSupplier.newBackoffBuilder().build();
    final JobCallbackCommandExceptionHandlingStrategy strategy =
        new JobCallbackCommandExceptionHandlingStrategy(backoff, executor);

    // maxRetries=1 means only 1 attempt allowed, no retries
    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 1);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();

    final StatusRuntimeException error = new StatusRuntimeException(Status.UNAVAILABLE);
    firstSend.completeExceptionally(error);

    assertThat(result).isDone();
    final CommandOutcome outcome = result.join();
    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    verify(executor, never()).schedule(any(Runnable.class), any(Long.class), any(TimeUnit.class));
  }

  @Test
  void jobDeadlineExceededNoRetryFailed() {
    final CompletableFuture<Object> sendFuture = new CompletableFuture<>();
    when(command.send()).thenReturn(asCamundaFuture(sendFuture));

    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final BackoffSupplier backoff = BackoffSupplier.newBackoffBuilder().build();
    final JobCallbackCommandExceptionHandlingStrategy strategy =
        new JobCallbackCommandExceptionHandlingStrategy(backoff, executor);

    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(command, 0L, strategy, metricsRecorder, metricsContext, 3);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();

    sendFuture.completeExceptionally(new StatusRuntimeException(Status.UNAVAILABLE));

    assertThat(result).isDone();
    assertThat(result.join()).isInstanceOf(CommandOutcome.Failed.class);
    verify(executor, never()).schedule(any(Runnable.class), any(Long.class), any(TimeUnit.class));
  }

  @Test
  void metricsIncreaserCalledOnlyOnSuccess() {
    final CompletableFuture<Object> sendFuture = new CompletableFuture<>();
    when(command.send()).thenReturn(asCamundaFuture(sendFuture));

    final JobCallbackCommandExceptionHandlingStrategy strategy =
        mock(JobCallbackCommandExceptionHandlingStrategy.class);

    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 3);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();

    final Object response = new Object();
    sendFuture.complete(response);

    assertThat(result).isDone();
    assertThat(result.join()).isInstanceOf(CommandOutcome.Completed.class);
    assertThat(((CommandOutcome.Completed) result.join()).response()).isSameAs(response);
  }

  @Test
  void metricsIncreaserNotCalledOnFailure() {
    final CompletableFuture<Object> sendFuture = new CompletableFuture<>();
    when(command.send()).thenReturn(asCamundaFuture(sendFuture));

    final JobCallbackCommandExceptionHandlingStrategy strategy =
        mock(JobCallbackCommandExceptionHandlingStrategy.class);
    final RuntimeException error = new RuntimeException("fail");
    when(strategy.handleCommandError(any(), any())).thenReturn(new CommandOutcome.Failed(error, 1));

    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 3);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();

    sendFuture.completeExceptionally(error);

    assertThat(result).isDone();
    assertThat(result.join()).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed) result.join()).cause()).isSameAs(error);
  }

  @Test
  void doubleExecuteThrowsIllegalStateException() {
    when(command.send()).thenReturn(asCamundaFuture(new CompletableFuture<>()));

    final JobCallbackCommandExceptionHandlingStrategy strategy =
        mock(JobCallbackCommandExceptionHandlingStrategy.class);
    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 3);

    wrapper.executeAsync();

    assertThatThrownBy(wrapper::executeAsync).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void doubleExecuteWithMetricsThrowsIllegalStateException() {
    when(command.send()).thenReturn(asCamundaFuture(new CompletableFuture<>()));

    final JobCallbackCommandExceptionHandlingStrategy strategy =
        mock(JobCallbackCommandExceptionHandlingStrategy.class);
    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 3);

    wrapper.executeAsync();

    assertThatThrownBy(wrapper::executeAsync).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void retryWithMetricsCallsIncreaserOnEventualSuccess() {
    final CompletableFuture<Object> firstSend = new CompletableFuture<>();
    final CompletableFuture<Object> secondSend = new CompletableFuture<>();
    when(command.send())
        .thenReturn(asCamundaFuture(firstSend))
        .thenReturn(asCamundaFuture(secondSend));

    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final BackoffSupplier backoff = BackoffSupplier.newBackoffBuilder().build();
    final JobCallbackCommandExceptionHandlingStrategy strategy =
        new JobCallbackCommandExceptionHandlingStrategy(backoff, executor);

    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 3);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();

    // First attempt fails
    firstSend.completeExceptionally(new StatusRuntimeException(Status.UNAVAILABLE));

    // Execute retry
    final ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).schedule(runnableCaptor.capture(), any(Long.class), any(TimeUnit.class));
    runnableCaptor.getValue().run();

    // Second attempt succeeds
    secondSend.complete(new Object());

    assertThat(result).isDone();
    assertThat(result.join()).isInstanceOf(CommandOutcome.Completed.class);
  }

  @Test
  void shouldIncreaseBackoffDelayOnConsecutiveRetries() {
    // given
    final CompletableFuture<Object> firstSend = new CompletableFuture<>();
    final CompletableFuture<Object> secondSend = new CompletableFuture<>();
    final CompletableFuture<Object> thirdSend = new CompletableFuture<>();
    when(command.send())
        .thenReturn(asCamundaFuture(firstSend))
        .thenReturn(asCamundaFuture(secondSend))
        .thenReturn(asCamundaFuture(thirdSend));

    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final BackoffSupplier backoff =
        BackoffSupplier.newBackoffBuilder().jitterFactor(0).backoffFactor(2).build();
    final JobCallbackCommandExceptionHandlingStrategy strategy =
        new JobCallbackCommandExceptionHandlingStrategy(backoff, executor);

    // initial delay in CommandWrapper is 50ms
    final JobCallbackCommandWrapper wrapper =
        new JobCallbackCommandWrapper(
            command, Long.MAX_VALUE, strategy, metricsRecorder, metricsContext, 5);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsync();

    // when — first failure triggers retry with delay 50 * 2 = 100ms
    firstSend.completeExceptionally(new StatusRuntimeException(Status.UNAVAILABLE));

    // then
    final ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
    final ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).schedule(runnableCaptor.capture(), delayCaptor.capture(), any(TimeUnit.class));
    assertThat(delayCaptor.getValue()).isEqualTo(100L);

    // when — second failure triggers retry with delay 100 * 2 = 200ms
    runnableCaptor.getValue().run();
    secondSend.completeExceptionally(new StatusRuntimeException(Status.UNAVAILABLE));

    // then
    verify(executor, times(2))
        .schedule(runnableCaptor.capture(), delayCaptor.capture(), any(TimeUnit.class));
    assertThat(delayCaptor.getValue()).isEqualTo(200L);

    // complete the third attempt successfully
    runnableCaptor.getValue().run();
    final Object response = new Object();
    thirdSend.complete(response);

    assertThat(result).isDone();
    assertThat(result.join()).isInstanceOf(CommandOutcome.Completed.class);
    assertThat(((CommandOutcome.Completed) result.join()).response()).isSameAs(response);
    assertThat(((CommandOutcome.Completed) result.join()).attempts()).isEqualTo(3);
  }

  private static <T> CamundaFuture<T> asCamundaFuture(final CompletableFuture<T> delegate) {
    return new TestCamundaFuture<>(delegate);
  }

  private static class TestCamundaFuture<T> extends CompletableFuture<T>
      implements CamundaFuture<T> {

    TestCamundaFuture(final CompletableFuture<T> delegate) {
      delegate.whenComplete(
          (result, error) -> {
            if (error != null) {
              completeExceptionally(error);
            } else {
              complete(result);
            }
          });
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning, final Throwable cause) {
      return cancel(mayInterruptIfRunning);
    }

    @Override
    public T join(final long timeout, final TimeUnit unit) {
      throw new UnsupportedOperationException();
    }
  }
}
