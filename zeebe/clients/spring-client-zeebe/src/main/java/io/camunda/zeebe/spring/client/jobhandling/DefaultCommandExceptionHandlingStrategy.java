package io.camunda.zeebe.spring.client.jobhandling;

import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultCommandExceptionHandlingStrategy implements CommandExceptionHandlingStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final Set<Status.Code> SUCCESS_CODES =
      EnumSet.of(Status.Code.OK, Status.Code.ALREADY_EXISTS);
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

  private BackoffSupplier backoffSupplier;
  private ScheduledExecutorService scheduledExecutorService;

  public DefaultCommandExceptionHandlingStrategy(
      BackoffSupplier backoffSupplier, ScheduledExecutorService scheduledExecutorService) {
    this.backoffSupplier = backoffSupplier;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  public void handleCommandError(CommandWrapper command, Throwable throwable) {
    if (StatusRuntimeException.class.isAssignableFrom(throwable.getClass())) {
      StatusRuntimeException exception = (StatusRuntimeException) throwable;
      Status.Code code = exception.getStatus().getCode();

      // Success codes should not lead to an exception!
      if (IGNORABLE_FAILURE_CODES.contains(code)) {
        LOG.warn(
            "Ignoring the error of type '"
                + code
                + "' during "
                + command
                + ". Job might have been canceled or already completed.");
        // TODO: IS Ignorance really a good idea? Think of some local transaction that might need to
        // be marked for rollback! But for sure, retry does not help at all
        return;
      } else if (RETRIABLE_CODES.contains(code)) {
        if (command.hasMoreRetries()) {
          command.increaseBackoffUsing(backoffSupplier);
          LOG.warn("Retrying " + command + " after error of type '" + code + "' with backoff");
          command.scheduleExecutionUsing(scheduledExecutorService);
          return;
        } else {
          throw new RuntimeException(
              "Could not execute "
                  + command
                  + " due to error of type '"
                  + code
                  + "' and no retries are left",
              throwable);
        }
      } else if (FAILURE_CODES.contains(code)) {
        throw new RuntimeException(
            "Could not execute " + command + " due to error of type '" + code + "'", throwable);
      }
    }

    // if it wasn't handled yet, throw an exception
    throw new RuntimeException(
        "Could not execute " + command + " due to exception: " + throwable.getMessage(), throwable);
  }
}
