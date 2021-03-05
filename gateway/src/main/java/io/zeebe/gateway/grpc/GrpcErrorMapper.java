/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.grpc;

import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.Status;
import com.google.rpc.Status.Builder;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.netty.channel.ConnectTimeoutException;
import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.cmd.BrokerErrorException;
import io.zeebe.gateway.cmd.BrokerRejectionException;
import io.zeebe.gateway.cmd.InvalidBrokerRequestArgumentException;
import io.zeebe.gateway.cmd.NoTopologyAvailableException;
import io.zeebe.gateway.cmd.PartitionNotFoundException;
import io.zeebe.gateway.impl.broker.RequestRetriesExhaustedException;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.msgpack.MsgpackPropertyException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

/** Maps arbitrary {@link Throwable} to {@link StatusRuntimeException} and logs the exception. */
public final class GrpcErrorMapper {
  public StatusRuntimeException mapError(final Throwable error) {
    return mapError(error, Loggers.GATEWAY_LOGGER);
  }

  StatusRuntimeException mapError(final Throwable error, final Logger logger) {
    return StatusProto.toStatusRuntimeException(mapErrorToStatus(error, logger));
  }

  private Status mapErrorToStatus(final Throwable error, final Logger logger) {
    return mapErrorToStatus(error, error, logger);
  }

  private Status mapErrorToStatus(
      final Throwable rootError, final Throwable error, final Logger logger) {
    final Builder builder = Status.newBuilder();

    if (error instanceof ExecutionException) {
      return mapErrorToStatus(rootError, error.getCause(), logger);
    } else if (error instanceof BrokerErrorException) {
      final Status status =
          mapBrokerErrorToStatus(rootError, ((BrokerErrorException) error).getError(), logger);
      builder.mergeFrom(status);
    } else if (error instanceof BrokerRejectionException) {
      final Status status = mapRejectionToStatus(((BrokerRejectionException) error).getRejection());
      builder.mergeFrom(status);
      logger.trace("Expected to handle gRPC request, but the broker rejected it", rootError);
    } else if (error instanceof TimeoutException) { // can be thrown by transport
      builder
          .setCode(Code.DEADLINE_EXCEEDED_VALUE)
          .setMessage("Time out between gateway and broker: " + error.getMessage());
      logger.debug(
          "Expected to handle gRPC request, but request timed out between gateway and broker",
          rootError);
    } else if (error instanceof InvalidBrokerRequestArgumentException) {
      builder.setCode(Code.INVALID_ARGUMENT_VALUE).setMessage(error.getMessage());
      logger.debug("Expected to handle gRPC request, but broker argument was invalid", rootError);
    } else if (error instanceof MsgpackPropertyException) {
      builder.setCode(Code.INVALID_ARGUMENT_VALUE).setMessage(error.getMessage());
      logger.debug(
          "Expected to handle gRPC request, but messagepack property was invalid", rootError);
    } else if (error instanceof PartitionNotFoundException) {
      builder.setCode(Code.UNAVAILABLE_VALUE).setMessage(error.getMessage());
      logger.debug(
          "Expected to handle gRPC request, but request could not be delivered", rootError);
    } else if (error instanceof RequestRetriesExhaustedException) {
      builder.setCode(Code.RESOURCE_EXHAUSTED_VALUE).setMessage(error.getMessage());

      // RequestRetriesExhaustedException will sometimes carry suppressed exceptions which can be
      // added/mapped as error details to give more information to the user
      for (final Throwable suppressed : error.getSuppressed()) {
        builder.addDetails(Any.pack(mapErrorToStatus(rootError, suppressed, logger)));
      }

      // this error occurs when all partitions have exhausted for requests which have no fixed
      // partitions - it will then also occur when back pressure kicks in, leading to a large burst
      // of error logs that is, in fact, expected
      logger.trace(
          "Expected to handle gRPC request, but all retries have been exhausted", rootError);
    } else if (error instanceof NoTopologyAvailableException) {
      builder.setCode(Code.UNAVAILABLE_VALUE).setMessage(error.getMessage());
      logger.trace(
          "Expected to handle gRPC request, but the gateway does not know any partitions yet",
          rootError);
    } else if (error instanceof ConnectTimeoutException) {
      builder.setCode(Code.UNAVAILABLE_VALUE).setMessage(error.getMessage());
      logger.warn(
          "Expected to handle gRPC request, but a connection timeout exception occurred",
          rootError);
    } else {
      builder
          .setCode(Code.INTERNAL_VALUE)
          .setMessage(
              "Unexpected error occurred during the request processing: " + error.getMessage());
      logger.error("Expected to handle gRPC request, but an unexpected error occurred", rootError);
    }

    return builder.build();
  }

  private Status mapBrokerErrorToStatus(
      final Throwable rootError, final BrokerError error, final Logger logger) {
    final Builder builder = Status.newBuilder();
    String message = error.getMessage();

    switch (error.getCode()) {
      case PROCESS_NOT_FOUND:
        builder.setCode(Code.NOT_FOUND_VALUE);
        break;
      case RESOURCE_EXHAUSTED:
        builder.setCode(Code.RESOURCE_EXHAUSTED_VALUE);
        logger.trace("Target broker is currently overloaded: {}", error, rootError);
        break;
      case PARTITION_LEADER_MISMATCH:
        // return UNAVAILABLE to indicate to the user that retrying might solve the issue, as this
        // is usually a transient issue
        logger.trace("Target broker was not the leader of the partition: {}", error, rootError);
        builder.setCode(Code.UNAVAILABLE_VALUE);
        break;
      default:
        // all the following are for cases where retrying (with the same gateway) is not expected
        // to solve anything
        logger.error(
            "Expected to handle gRPC request, but received an internal error from broker: {}",
            error,
            rootError);
        builder.setCode(Code.INTERNAL_VALUE);
        message =
            String.format(
                "Unexpected error occurred between gateway and broker (code: %s)", error.getCode());
        break;
    }

    return builder.setMessage(message).build();
  }

  private Status mapRejectionToStatus(final BrokerRejection rejection) {
    final String message =
        String.format(
            "Command '%s' rejected with code '%s': %s",
            rejection.getIntent(), rejection.getType(), rejection.getReason());
    final Builder builder = Status.newBuilder().setMessage(message);

    switch (rejection.getType()) {
      case INVALID_ARGUMENT:
        builder.setCode(Code.INVALID_ARGUMENT_VALUE);
        break;
      case NOT_FOUND:
        builder.setCode(Code.NOT_FOUND_VALUE);
        break;
      case ALREADY_EXISTS:
        builder.setCode(Code.ALREADY_EXISTS_VALUE);
        break;
      case INVALID_STATE:
        builder.setCode(Code.FAILED_PRECONDITION_VALUE);
        break;
      case PROCESSING_ERROR:
        builder.setCode(Code.INTERNAL_VALUE);
        break;
      case SBE_UNKNOWN:
      case NULL_VAL:
      default:
        builder.setCode(Code.UNKNOWN_VALUE);
        break;
    }

    return builder.build();
  }
}
