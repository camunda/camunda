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
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
public class CommandWrapperTest {

  private FinalCommandStep<Object> command;
  private ActivatedJob job;
  private MetricsRecorder metricsRecorder;
  private CounterMetricsContext metricsContext;

  @BeforeEach
  void setUp() {
    command = mock(FinalCommandStep.class);
    job = mock(ActivatedJob.class);
    when(job.getDeadline()).thenReturn(Long.MAX_VALUE);
    metricsRecorder = new DefaultNoopMetricsRecorder();
    metricsContext = mock(CounterMetricsContext.class);
  }

  @Test
  void immediateSuccessCompletesWithCompleted() {
    final CompletableFuture<Object> sendFuture = new CompletableFuture<>();
    when(command.send()).thenReturn(asCamundaFuture(sendFuture));

    final CommandExceptionHandlingStrategy strategy = mock(CommandExceptionHandlingStrategy.class);
    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

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

    final CommandExceptionHandlingStrategy strategy = mock(CommandExceptionHandlingStrategy.class);
    final StatusRuntimeException error = new StatusRuntimeException(Status.INTERNAL);
    when(strategy.handleCommandError(any(), any())).thenReturn(new CommandOutcome.Failed(error, 1));

    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

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

    final CommandExceptionHandlingStrategy strategy = mock(CommandExceptionHandlingStrategy.class);
    final StatusRuntimeException error = new StatusRuntimeException(Status.NOT_FOUND);
    when(strategy.handleCommandError(any(), any()))
        .thenReturn(new CommandOutcome.Ignored(error, 1));

    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

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
    final DefaultCommandExceptionHandlingStrategy strategy =
        new DefaultCommandExceptionHandlingStrategy(backoff, executor);

    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

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
    final DefaultCommandExceptionHandlingStrategy strategy =
        new DefaultCommandExceptionHandlingStrategy(backoff, executor);

    // maxRetries=1 means only 1 attempt allowed, no retries
    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 1);

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
    // deadline in the past
    when(job.getDeadline()).thenReturn(0L);

    final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    final BackoffSupplier backoff = BackoffSupplier.newBackoffBuilder().build();
    final DefaultCommandExceptionHandlingStrategy strategy =
        new DefaultCommandExceptionHandlingStrategy(backoff, executor);

    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

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

    final CommandExceptionHandlingStrategy strategy = mock(CommandExceptionHandlingStrategy.class);
    final BiConsumer<MetricsRecorder, CounterMetricsContext> increaser = mock(BiConsumer.class);

    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

    wrapper.executeAsyncWithMetrics(increaser);

    sendFuture.complete(new Object());

    verify(increaser, times(1)).accept(metricsRecorder, metricsContext);
  }

  @Test
  void metricsIncreaserNotCalledOnFailure() {
    final CompletableFuture<Object> sendFuture = new CompletableFuture<>();
    when(command.send()).thenReturn(asCamundaFuture(sendFuture));

    final CommandExceptionHandlingStrategy strategy = mock(CommandExceptionHandlingStrategy.class);
    when(strategy.handleCommandError(any(), any()))
        .thenReturn(new CommandOutcome.Failed(new RuntimeException(), 1));
    final BiConsumer<MetricsRecorder, CounterMetricsContext> increaser = mock(BiConsumer.class);

    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

    wrapper.executeAsyncWithMetrics(increaser);

    sendFuture.completeExceptionally(new RuntimeException("fail"));

    verify(increaser, never()).accept(any(), any());
  }

  @Test
  void doubleExecuteThrowsIllegalStateException() {
    when(command.send()).thenReturn(asCamundaFuture(new CompletableFuture<>()));

    final CommandExceptionHandlingStrategy strategy = mock(CommandExceptionHandlingStrategy.class);
    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

    wrapper.executeAsync();

    assertThatThrownBy(wrapper::executeAsync).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void doubleExecuteWithMetricsThrowsIllegalStateException() {
    when(command.send()).thenReturn(asCamundaFuture(new CompletableFuture<>()));

    final CommandExceptionHandlingStrategy strategy = mock(CommandExceptionHandlingStrategy.class);
    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

    wrapper.executeAsync();

    assertThatThrownBy(() -> wrapper.executeAsyncWithMetrics(MetricsRecorder::increaseCompleted))
        .isInstanceOf(IllegalStateException.class);
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
    final DefaultCommandExceptionHandlingStrategy strategy =
        new DefaultCommandExceptionHandlingStrategy(backoff, executor);

    final BiConsumer<MetricsRecorder, CounterMetricsContext> increaser = mock(BiConsumer.class);
    final CommandWrapper wrapper =
        new CommandWrapper(command, job, strategy, metricsRecorder, metricsContext, 3);

    final CompletableFuture<CommandOutcome> result = wrapper.executeAsyncWithMetrics(increaser);

    // First attempt fails
    firstSend.completeExceptionally(new StatusRuntimeException(Status.UNAVAILABLE));
    verify(increaser, never()).accept(any(), any());

    // Execute retry
    final ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).schedule(runnableCaptor.capture(), any(Long.class), any(TimeUnit.class));
    runnableCaptor.getValue().run();

    // Second attempt succeeds
    secondSend.complete(new Object());

    assertThat(result).isDone();
    assertThat(result.join()).isInstanceOf(CommandOutcome.Completed.class);
    verify(increaser, times(1)).accept(metricsRecorder, metricsContext);
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
    public T join(final long timeout, final TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning, final Throwable cause) {
      return cancel(mayInterruptIfRunning);
    }
  }
}
