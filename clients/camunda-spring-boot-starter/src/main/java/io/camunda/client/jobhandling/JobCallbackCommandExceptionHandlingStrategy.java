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

import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.worker.BackoffSupplier;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobCallbackCommandExceptionHandlingStrategy {
  public static final Predicate<Integer> REST_RETRYABLE =
      code -> Set.of(429, 502, 503, 504).contains(code);
  public static final Predicate<Integer> REST_IGNORABLE = code -> code == 404;
  public static final Predicate<Integer> REST_FAILURE = code -> code >= 400 && code <= 499;

  public static final Set<Status.Code> RETRIABLE_CODES =
      EnumSet.of(
          Status.Code.CANCELLED,
          Status.Code.DEADLINE_EXCEEDED,
          Status.Code.RESOURCE_EXHAUSTED,
          Status.Code.ABORTED,
          Status.Code.UNAVAILABLE,
          Status.Code.DATA_LOSS);
  public static final Set<Status.Code> IGNORABLE_FAILURE_CODES = EnumSet.of(Status.Code.NOT_FOUND);
  public static final Set<Status.Code> FAILURE_CODES =
      EnumSet.of(
          Status.Code.INVALID_ARGUMENT,
          Status.Code.PERMISSION_DENIED,
          Status.Code.FAILED_PRECONDITION,
          Status.Code.OUT_OF_RANGE,
          Status.Code.UNIMPLEMENTED,
          Status.Code.INTERNAL,
          Status.Code.UNAUTHENTICATED);
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final BackoffSupplier backoffSupplier;
  private final ScheduledExecutorService scheduledExecutorService;

  public JobCallbackCommandExceptionHandlingStrategy(
      final BackoffSupplier backoffSupplier,
      final ScheduledExecutorService scheduledExecutorService) {
    this.backoffSupplier = backoffSupplier;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  public CommandOutcome handleCommandError(
      final JobCallbackCommandWrapper command, final Throwable throwable) {
    if (throwable instanceof final StatusRuntimeException exception) {
      return handleGrpcError(command, exception);
    }
    if (throwable instanceof final ClientHttpException exception) {
      return handleRestError(command, exception);
    }
    LOG.error("Failed to execute {} due to unexpected exception", command, throwable);
    return new CommandOutcome.Failed(throwable, command.getAttempts());
  }

  private CommandOutcome handleRestError(
      final JobCallbackCommandWrapper command, final ClientHttpException exception) {
    final int code = exception.code();
    return handleError(
        command, exception, "http status code", code, REST_IGNORABLE, REST_RETRYABLE, REST_FAILURE);
  }

  private CommandOutcome handleGrpcError(
      final JobCallbackCommandWrapper command, final StatusRuntimeException exception) {
    final Status.Code code = exception.getStatus().getCode();
    return handleError(
        command,
        exception,
        "gRPC status code",
        code,
        IGNORABLE_FAILURE_CODES::contains,
        RETRIABLE_CODES::contains,
        FAILURE_CODES::contains);
  }

  private <T> CommandOutcome handleError(
      final JobCallbackCommandWrapper command,
      final Exception exception,
      final String codeType,
      final T code,
      final Predicate<T> ignorableCodes,
      final Predicate<T> retryableCodes,
      final Predicate<T> failureCodes) {
    if (ignorableCodes.test(code)) {
      LOG.debug("Ignoring {} with {} '{}'", command, codeType, code);
      return new CommandOutcome.Ignored(exception, command.getAttempts());
    }

    if (retryableCodes.test(code)) {
      if (!command.hasMoreRetries()) {
        LOG.error(
            "Failed to execute {} after {} attempts, {} '{}'",
            command,
            command.getAttempts(),
            codeType,
            code,
            exception);
        return new CommandOutcome.Failed(exception, command.getAttempts());
      }

      command.increaseBackoffUsing(backoffSupplier);
      LOG.warn("Retrying {} after {} '{}' with backoff", command, codeType, code);
      command.scheduleExecutionUsing(scheduledExecutorService);
      return new CommandOutcome.Retried(exception, command.getAttempts()); // retry scheduled
    }

    if (failureCodes.test(code)) {
      LOG.error(
          "Failed to execute {} due to non-retriable {} '{}'", command, codeType, code, exception);
      return new CommandOutcome.Failed(exception, command.getAttempts());
    }

    LOG.error("Failed to execute {} due to unexpected {} '{}'", command, codeType, code, exception);
    return new CommandOutcome.Failed(exception, command.getAttempts());
  }
}
