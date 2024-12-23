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
package io.camunda.spring.client.jobhandling;

import io.camunda.client.api.worker.BackoffSupplier;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultCommandExceptionHandlingStrategy implements CommandExceptionHandlingStrategy {

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

  public DefaultCommandExceptionHandlingStrategy(
      final BackoffSupplier backoffSupplier,
      final ScheduledExecutorService scheduledExecutorService) {
    this.backoffSupplier = backoffSupplier;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  @Override
  public void handleCommandError(final CommandWrapper command, final Throwable throwable) {
    if (StatusRuntimeException.class.isAssignableFrom(throwable.getClass())) {
      final StatusRuntimeException exception = (StatusRuntimeException) throwable;
      final Status.Code code = exception.getStatus().getCode();

      if (!RETRIABLE_CODES.contains(code)
          || !IGNORABLE_FAILURE_CODES.contains(code)
          || FAILURE_CODES.contains(code)) {
        throw new RuntimeException(
            "Could not execute " + command + " due to exception: " + throwable.getMessage(),
            throwable);
      }

      if (RETRIABLE_CODES.contains(code)) {
        if (!command.hasMoreRetries()) {
          throw new RuntimeException(
              "Could not execute "
                  + command
                  + " due to error of type '"
                  + code
                  + "' and no retries are left",
              throwable);
        }
        command.increaseBackoffUsing(backoffSupplier);
        LOG.warn("Retrying {} after error of type '{}' with backoff", command, code);
        command.scheduleExecutionUsing(scheduledExecutorService);
        return;
      }

      throw new RuntimeException(
          "Could not execute " + command + " due to error of type '" + code + "'", throwable);
    }
  }
}
