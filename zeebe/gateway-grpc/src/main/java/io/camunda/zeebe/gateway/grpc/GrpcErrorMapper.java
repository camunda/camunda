/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.grpc;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.Status;
import com.google.rpc.Status.Builder;
import io.atomix.cluster.messaging.MessagingException;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.NoTopologyAvailableException;
import io.camunda.zeebe.broker.client.api.PartitionInactiveException;
import io.camunda.zeebe.broker.client.api.PartitionNotFoundException;
import io.camunda.zeebe.broker.client.api.RequestRetriesExhaustedException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.cmd.IllegalTenantRequestException;
import io.camunda.zeebe.gateway.cmd.InvalidTenantRequestException;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

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

    switch (error) {
      case final ExecutionException e -> {
        return mapErrorToStatus(rootError, e.getCause(), logger);
      }
      case final BrokerErrorException brokerError -> {
        final Status status = mapBrokerErrorToStatus(rootError, brokerError.getError(), logger);
        builder.mergeFrom(status);
      }
      case final BrokerRejectionException rejection -> {
        final Status status = mapRejectionToStatus(rejection.getRejection());
        builder.mergeFrom(status);
        logger.trace("Expected to handle gRPC request, but the broker rejected it", rootError);
      }
      case final TimeoutException ignored -> {
        builder
            .setCode(Code.DEADLINE_EXCEEDED_VALUE)
            .setMessage("Time out between gateway and broker: " + error.getMessage());
        logger.debug(
            "Expected to handle gRPC request, but request timed out between gateway and broker",
            rootError);
      }
      case final MsgpackPropertyException ignored -> {
        builder.setCode(Code.INVALID_ARGUMENT_VALUE).setMessage(error.getMessage());
        logger.debug(
            "Expected to handle gRPC request, but messagepack property was invalid", rootError);
      }
      case final JsonParseException ignored -> {
        builder.setCode(Code.INVALID_ARGUMENT_VALUE).setMessage(error.getMessage());
        logger.debug("Expected to handle gRPC request, but JSON property was invalid", rootError);
      }
      case final InvalidTenantRequestException ignored -> {
        builder.setCode(Code.INVALID_ARGUMENT_VALUE).setMessage(error.getMessage());
        logger.debug(error.getMessage(), rootError);
      }
      case final IllegalTenantRequestException ignored -> {
        builder.setCode(Code.PERMISSION_DENIED_VALUE).setMessage(error.getMessage());
        logger.debug(error.getMessage(), rootError);
      }
      case final IllegalArgumentException ignored -> {
        builder.setCode(Code.INVALID_ARGUMENT_VALUE).setMessage(error.getMessage());
        logger.debug("Expected to handle gRPC request, but JSON property was invalid", rootError);
      }
      case final PartitionNotFoundException ignored -> {
        builder.setCode(Code.UNAVAILABLE_VALUE).setMessage(error.getMessage());
        logger.debug(
            "Expected to handle gRPC request, but request could not be delivered", rootError);
      }
      case final RequestRetriesExhaustedException ignored -> {
        builder.setCode(Code.RESOURCE_EXHAUSTED_VALUE).setMessage(error.getMessage());

        // RequestRetriesExhaustedException will sometimes carry suppressed exceptions which can be
        // added/mapped as error details to give more information to the user
        for (final Throwable suppressed : error.getSuppressed()) {
          builder.addDetails(
              Any.pack(mapErrorToStatus(rootError, suppressed, NOPLogger.NOP_LOGGER)));
        }

        // this error occurs when all partitions have exhausted for requests which have no fixed
        // partitions - it will then also occur when back pressure kicks in, leading to a large
        // burst
        // of error logs that is, in fact, expected
        logger.trace(
            "Expected to handle gRPC request, but all retries have been exhausted", rootError);
      }
      case final PartitionInactiveException ignored -> {
        builder.setCode(Code.UNAVAILABLE_VALUE).setMessage(error.getMessage());
        logger.trace(
            "Expected to handle gRPC request, but the target partition is currently inactive",
            rootError);
      }
      case final NoTopologyAvailableException ignored -> {
        builder.setCode(Code.UNAVAILABLE_VALUE).setMessage(error.getMessage());
        logger.trace(
            "Expected to handle gRPC request, but the gateway does not know any partitions yet",
            rootError);
      }
      case final ConnectTimeoutException ignored -> {
        builder.setCode(Code.UNAVAILABLE_VALUE).setMessage(error.getMessage());
        logger.warn(
            "Expected to handle gRPC request, but a connection timeout exception occurred",
            rootError);
      }
      case final ConnectException ignored -> {
        builder.setCode(Code.UNAVAILABLE_VALUE).setMessage(error.getMessage());
        logger.warn(
            "Expected to handle gRPC request, but there was a connection error with one of the brokers",
            rootError);
      }
      case final MessagingException.ConnectionClosed ignored -> {
        builder.setCode(Code.ABORTED_VALUE).setMessage(error.getMessage());
        logger.warn(
            "Expected to handle gRPC request, but the connection was cut prematurely with the broker; "
                + "the request may or may not have been accepted, and may not be safe to retry.",
            rootError);
      }
      default -> {
        builder
            .setCode(Code.INTERNAL_VALUE)
            .setMessage(
                "Unexpected error occurred during the request processing: " + error.getMessage());
        logger.error(
            "Expected to handle gRPC request, but an unexpected error occurred", rootError);
      }
    }

    return builder.build();
  }

  private Status mapBrokerErrorToStatus(
      final Throwable rootError, final BrokerError error, final Logger logger) {
    final Builder builder = Status.newBuilder();
    String message = error.getMessage();

    switch (error.getCode()) {
      case PROCESS_NOT_FOUND -> builder.setCode(Code.NOT_FOUND_VALUE);
      case RESOURCE_EXHAUSTED -> {
        builder.setCode(Code.RESOURCE_EXHAUSTED_VALUE);
        logger.trace("Target broker is currently overloaded: {}", error, rootError);
      }
      case PARTITION_LEADER_MISMATCH -> {
        // return UNAVAILABLE to indicate to the user that retrying might solve the issue, as this
        // is usually a transient issue
        logger.trace("Target broker was not the leader of the partition: {}", error, rootError);
        builder.setCode(Code.UNAVAILABLE_VALUE);
      }
      case MALFORMED_REQUEST -> builder.setCode(Code.INVALID_ARGUMENT_VALUE);
      case PARTITION_UNAVAILABLE -> {
        logger.debug("Partition is currently unavailable: {}", error, rootError);
        builder.setCode(Code.UNAVAILABLE_VALUE);
      }
      case MAX_MESSAGE_SIZE_EXCEEDED -> {
        logger.debug("Max message size exceeded: {}", error, rootError);
        builder.setCode(Code.RESOURCE_EXHAUSTED_VALUE);
      }
      default -> {
        // all the following are for cases where retrying (with the same gateway) is not expected
        // to solve anything
        logger.error(
            "Expected to handle gRPC request, but received an internal error from broker: {}",
            error,
            rootError);
        builder.setCode(Code.INTERNAL_VALUE);
        message =
            String.format(
                "Unexpected error occurred between gateway and broker (code: %s) (message: %s)",
                error.getCode(), error.getMessage());
      }
    }

    return builder.setMessage(message).build();
  }

  private Status mapRejectionToStatus(final BrokerRejection rejection) {
    final String message =
        String.format(
            "Command '%s' rejected with code '%s': %s",
            rejection.intent(), rejection.type(), rejection.reason());
    final Builder builder = Status.newBuilder().setMessage(message);

    switch (rejection.type()) {
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
      case UNAUTHORIZED:
      case FORBIDDEN:
        builder.setCode(Code.PERMISSION_DENIED_VALUE);
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
