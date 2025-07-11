/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

import static io.camunda.service.exception.ServiceException.Status.*;

import com.fasterxml.jackson.core.JsonParseException;
import io.atomix.cluster.messaging.MessagingException;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.DocumentAlreadyExists;
import io.camunda.document.api.DocumentError.DocumentNotFound;
import io.camunda.document.api.DocumentError.InvalidInput;
import io.camunda.document.api.DocumentError.OperationNotSupported;
import io.camunda.document.api.DocumentError.StoreDoesNotExist;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.auth.Authorization;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.NoTopologyAvailableException;
import io.camunda.zeebe.broker.client.api.PartitionInactiveException;
import io.camunda.zeebe.broker.client.api.PartitionNotFoundException;
import io.camunda.zeebe.broker.client.api.RequestRetriesExhaustedException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.cmd.IllegalTenantRequestException;
import io.camunda.zeebe.gateway.cmd.InvalidTenantRequestException;
import io.camunda.zeebe.msgpack.MsgpackException;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ErrorMapper.class);

  public static ServiceException mapError(final Throwable error) {
    return new ServiceException(mapErrorToServiceError(error));
  }

  public static ServiceException mapBrokerError(final BrokerError error) {
    return new ServiceException(mapBrokerErrorToServiceError(null, error, LOGGER));
  }

  public static ServiceException mapBrokerRejection(final BrokerRejection rejection) {
    return new ServiceException(mapRejectionToServiceError(rejection));
  }

  public static ServiceException mapDocumentError(final DocumentError documentError) {
    return new ServiceException(mapDocumentErrorToServiceError(documentError));
  }

  public static ServiceException mapSearchError(final CamundaSearchException cse) {
    final String errorMessage = cse.getMessage();
    return switch (cse.getReason()) {
      case NOT_FOUND -> new ServiceException(errorMessage, NOT_FOUND);
      case NOT_UNIQUE -> new ServiceException(errorMessage, ALREADY_EXISTS);
      case CONNECTION_FAILED -> {
        final String detail = "The search client could not connect to the search server";
        LOGGER.debug(detail, cse);
        yield new ServiceException(detail, UNAVAILABLE);
      }
      case SEARCH_SERVER_FAILED -> {
        final String detail = "The search server was unable to process the request";
        LOGGER.debug(detail, cse);
        yield new ServiceException(detail, INTERNAL);
      }
      case SEARCH_CLIENT_FAILED -> {
        final String detail = "The search client was unable to process the request";
        LOGGER.debug(detail, cse);
        yield new ServiceException(detail, INTERNAL);
      }
      default -> {
        LOGGER.debug(errorMessage, cse);
        yield new ServiceException(errorMessage, INTERNAL);
      }
    };
  }

  public static ServiceException createForbiddenException(final Authorization authorization) {
    return new ServiceException(
        "Unauthorized to perform operation '%s' on resource '%s'"
            .formatted(authorization.permissionType(), authorization.resourceType()),
        FORBIDDEN);
  }

  private static ServiceError mapErrorToServiceError(final Throwable error) {
    return mapErrorToServiceError(error, error, ErrorMapper.LOGGER);
  }

  private static ServiceError mapErrorToServiceError(
      final Throwable rootError, final Throwable error, final Logger logger) {
    final var builder = ServiceError.newBuilder();

    switch (error) {
      case final ExecutionException e -> {
        return mapErrorToServiceError(rootError, e.getCause(), logger);
      }
      case final CompletionException e -> {
        return mapErrorToServiceError(rootError, e.getCause(), logger);
      }
      case final BrokerErrorException brokerError -> {
        final ServiceError serviceError =
            mapBrokerErrorToServiceError(rootError, brokerError.getError(), logger);
        builder.mergeFrom(serviceError);
        logger.trace("Expected to handle request, but the broker rejected it", rootError);
      }
      case final BrokerRejectionException rejection -> {
        final ServiceError serviceError = mapRejectionToServiceError(rejection.getRejection());
        builder.mergeFrom(serviceError);
        logger.trace("Expected to handle request, but the broker rejected it", rootError);
      }
      case final TimeoutException ignored -> {
        final var message =
            "Expected to handle request, but request timed out between gateway and broker";
        builder.status(DEADLINE_EXCEEDED).message(message);
        logger.debug(message, rootError);
      }
      case final MsgpackException ignored -> {
        final var message = "Expected to handle request, but messagepack property was invalid";
        builder.status(INVALID_ARGUMENT).message(message);
        logger.debug(message, rootError);
      }
      case final io.camunda.zeebe.msgpack.spec.MsgpackException ignored -> {
        final var message = "Expected to handle request, but messagepack property was invalid";
        builder.status(INVALID_ARGUMENT).message(message);
        logger.debug(message, rootError);
      }
      case final JsonParseException ignored -> {
        final var message = "Expected to handle request, but JSON property was invalid";
        builder.status(INVALID_ARGUMENT).message(message);
        logger.debug(message, rootError);
      }
      case final InvalidTenantRequestException ignored -> {
        builder.status(INVALID_ARGUMENT).message(error.getMessage());
        logger.debug(error.getMessage(), rootError);
      }
      case final IllegalTenantRequestException ignored -> {
        builder.status(UNAUTHORIZED).message(error.getMessage());
        logger.debug(error.getMessage(), rootError);
      }
      case final IllegalArgumentException ignored -> {
        final var message = "Expected to handle request, but JSON property was invalid";
        builder.status(INVALID_ARGUMENT).message(message);
        logger.debug(message, rootError);
      }
      case final PartitionNotFoundException ignored -> {
        final var message = "Expected to handle request, but request could not be delivered";
        builder.status(UNAVAILABLE).message(message);
        logger.debug(message, rootError);
      }
      case final RequestRetriesExhaustedException ignored -> {
        final var message = "Expected to handle request, but all retries have been exhausted";
        builder.status(RESOURCE_EXHAUSTED).message(message);
        // this error occurs when all partitions have exhausted for requests which have no fixed
        // partitions - it will then also occur when back pressure kicks in, leading to a large
        // burst
        // of error logs that is, in fact, expected
        logger.trace(message, rootError);
      }
      case final PartitionInactiveException ignored -> {
        final var message =
            "Expected to handle request, but the target partition is currently inactive";
        builder.status(UNAVAILABLE).message(message);
        logger.trace(message, rootError);
      }
      case final NoTopologyAvailableException ignored -> {
        final var message =
            "Expected to handle request, but the gateway does not know any partitions yet";
        builder.status(UNAVAILABLE).message(message);
        logger.trace(message, rootError);
      }
      case final ConnectTimeoutException ignored -> {
        final var message =
            "Expected to handle request, but a connection timeout exception occurred";
        builder.status(UNAVAILABLE).message(message);
        logger.warn(message, rootError);
      }
      case final ConnectException ignored -> {
        final var message =
            "Expected to handle request, but there was a connection error with one of the brokers";
        builder.status(UNAVAILABLE).message(message);
        logger.warn(message, rootError);
      }
      case final MessagingException.ConnectionClosed ignored -> {
        final var message =
            "Expected to handle request, but the connection was cut prematurely with the broker; "
                + "the request may or may not have been accepted, and may not be safe to retry.";
        builder.status(ABORTED).message(message);
        logger.warn(message, rootError);
      }
      default -> {
        builder
            .status(INTERNAL)
            .message(
                "Unexpected error occurred during the request processing: " + error.getMessage());
        logger.error("Expected to handle request, but an unexpected error occurred", rootError);
      }
    }

    return builder.build();
  }

  private static ServiceError mapBrokerErrorToServiceError(
      final Throwable rootError, final BrokerError error, final Logger logger) {
    final var builder = ServiceError.newBuilder();
    String message = error.getMessage();

    switch (error.getCode()) {
      case PROCESS_NOT_FOUND -> builder.status(NOT_FOUND);
      case RESOURCE_EXHAUSTED -> {
        builder.status(RESOURCE_EXHAUSTED);
        logger.trace("Target broker is currently overloaded: {}", error, rootError);
      }
      case PARTITION_LEADER_MISMATCH -> {
        // return UNAVAILABLE to indicate to the user that retrying might solve the issue, as this
        // is usually a transient issue
        logger.trace("Target broker was not the leader of the partition: {}", error, rootError);
        builder.status(UNAVAILABLE);
      }
      case MALFORMED_REQUEST -> {
        logger.debug("Malformed request: {}", message, rootError);
        builder.status(INVALID_ARGUMENT);
      }
      case PARTITION_UNAVAILABLE -> {
        logger.debug("Partition is currently unavailable: {}", error, rootError);
        builder.status(UNAVAILABLE);
      }
      default -> {
        // all the following are for cases where retrying (with the same gateway) is not expected
        // to solve anything
        logger.error(
            "Expected to handle request, but received an internal error from broker: {}",
            error,
            rootError);
        builder.status(INTERNAL);
        message =
            String.format(
                "Unexpected error occurred between gateway and broker (code: %s) (message: %s)",
                error.getCode(), error.getMessage());
      }
    }

    return builder.message(message).build();
  }

  private static ServiceError mapRejectionToServiceError(final BrokerRejection rejection) {
    final String message =
        String.format(
            "Command '%s' rejected with code '%s': %s",
            rejection.intent(), rejection.type(), rejection.reason());
    final var builder = ServiceError.newBuilder().message(message);

    return switch (rejection.type()) {
      case INVALID_ARGUMENT -> builder.status(INVALID_ARGUMENT).build();
      case NOT_FOUND -> builder.status(NOT_FOUND).build();
      case ALREADY_EXISTS -> builder.status(ALREADY_EXISTS).build();
      case INVALID_STATE -> builder.status(INVALID_STATE).build();
      case PROCESSING_ERROR, EXCEEDED_BATCH_RECORD_SIZE -> builder.status(INTERNAL).build();
      case UNAUTHORIZED -> builder.status(UNAUTHORIZED).build();
      case FORBIDDEN -> builder.status(FORBIDDEN).build();
      default -> builder.status(UNKNOWN).build();
    };
  }

  private static ServiceError mapDocumentErrorToServiceError(final DocumentError documentError) {
    return switch (documentError) {
      case final DocumentNotFound notFound ->
          new ServiceError(
              String.format("Document with id '%s' not found", notFound.documentId()), NOT_FOUND);
      case final InvalidInput invalidInput ->
          new ServiceError(invalidInput.message(), INVALID_ARGUMENT);
      case final DocumentAlreadyExists documentAlreadyExists ->
          new ServiceError(
              String.format(
                  "Document with id '%s' already exists", documentAlreadyExists.documentId()),
              ALREADY_EXISTS);
      case final StoreDoesNotExist storeDoesNotExist ->
          new ServiceError(
              String.format(
                  "Document store with id '%s' does not exist", storeDoesNotExist.storeId()),
              INVALID_ARGUMENT);
      case final OperationNotSupported operationNotSupported ->
          new ServiceError(operationNotSupported.message(), FORBIDDEN);
      default -> new ServiceError("Unexpected error occurred when handling document", INTERNAL);
    };
  }
}
