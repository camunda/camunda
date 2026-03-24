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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.worker.BackoffSupplier;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class DefaultCommandExceptionHandlingStrategyTest {

  private BackoffSupplier backoffSupplier;
  private ScheduledExecutorService executor;
  private DefaultCommandExceptionHandlingStrategy strategy;

  @BeforeEach
  void setUp() {
    backoffSupplier = BackoffSupplier.newBackoffBuilder().build();
    executor = mock(ScheduledExecutorService.class);
    strategy = new DefaultCommandExceptionHandlingStrategy(backoffSupplier, executor);
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.Code.class,
      names = {"NOT_FOUND"})
  void shouldReturnIgnoredForIgnorableStatusCodes(final Status.Code code) {
    // given
    final CommandWrapper command = commandWithRetries();
    final StatusRuntimeException exception = new StatusRuntimeException(code.toStatus());

    // when
    final CommandOutcome outcome = strategy.handleCommandError(command, exception);

    // then
    assertThat(outcome).isInstanceOf(CommandOutcome.Ignored.class);
    assertThat(outcome.attempts()).isEqualTo(1);
    assertThat(((CommandOutcome.Ignored) outcome).cause()).isSameAs(exception);
    verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.Code.class,
      names = {
        "CANCELLED",
        "DEADLINE_EXCEEDED",
        "RESOURCE_EXHAUSTED",
        "ABORTED",
        "UNAVAILABLE",
        "DATA_LOSS"
      })
  void shouldScheduleRetryForRetriableStatusCodes(final Status.Code code) {
    // given
    final CommandWrapper command = commandWithRetries();
    final StatusRuntimeException exception = new StatusRuntimeException(code.toStatus());

    // when
    final CommandOutcome outcome = strategy.handleCommandError(command, exception);

    // then
    assertThat(outcome).isNull();
    verify(command).increaseBackoffUsing(backoffSupplier);
    verify(command).scheduleExecutionUsing(executor);
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.Code.class,
      names = {
        "CANCELLED",
        "DEADLINE_EXCEEDED",
        "RESOURCE_EXHAUSTED",
        "ABORTED",
        "UNAVAILABLE",
        "DATA_LOSS"
      })
  void shouldReturnFailedWhenRetriesExhaustedForRetriableStatusCodes(final Status.Code code) {
    // given
    final CommandWrapper command = commandWithoutRetries();
    final StatusRuntimeException exception = new StatusRuntimeException(code.toStatus());

    // when
    final CommandOutcome outcome = strategy.handleCommandError(command, exception);

    // then
    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed) outcome).cause()).isSameAs(exception);
    verify(command, never()).scheduleExecutionUsing(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.Code.class,
      names = {
        "INVALID_ARGUMENT",
        "PERMISSION_DENIED",
        "FAILED_PRECONDITION",
        "OUT_OF_RANGE",
        "UNIMPLEMENTED",
        "INTERNAL",
        "UNAUTHENTICATED"
      })
  void shouldReturnFailedForNonRetriableFailureStatusCodes(final Status.Code code) {
    // given
    final CommandWrapper command = commandWithRetries();
    final StatusRuntimeException exception = new StatusRuntimeException(code.toStatus());

    // when
    final CommandOutcome outcome = strategy.handleCommandError(command, exception);

    // then
    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed) outcome).cause()).isSameAs(exception);
    verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.Code.class,
      names = {"OK", "UNKNOWN", "ALREADY_EXISTS"})
  void shouldReturnFailedForUnexpectedStatusCodes(final Status.Code code) {
    // given
    final CommandWrapper command = commandWithRetries();
    final StatusRuntimeException exception = new StatusRuntimeException(code.toStatus());

    // when
    final CommandOutcome outcome = strategy.handleCommandError(command, exception);

    // then
    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed) outcome).cause()).isSameAs(exception);
    verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  @Test
  void shouldReturnFailedForNonGrpcException() {
    // given
    final CommandWrapper command = commandWithRetries();
    final RuntimeException exception = new RuntimeException("connection reset");

    // when
    final CommandOutcome outcome = strategy.handleCommandError(command, exception);

    // then
    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed) outcome).cause()).isSameAs(exception);
    verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  private CommandWrapper commandWithRetries() {
    final CommandWrapper command = mock(CommandWrapper.class);
    when(command.hasMoreRetries()).thenReturn(true);
    when(command.getAttempts()).thenReturn(1);
    return command;
  }

  private CommandWrapper commandWithoutRetries() {
    final CommandWrapper command = mock(CommandWrapper.class);
    when(command.hasMoreRetries()).thenReturn(false);
    when(command.getAttempts()).thenReturn(3);
    return command;
  }
}
