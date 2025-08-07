/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

import static io.camunda.service.exception.ServiceException.Status.*;
import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;

import com.fasterxml.jackson.core.JsonParseException;
import io.atomix.cluster.messaging.MessagingException;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.DocumentAlreadyExists;
import io.camunda.document.api.DocumentError.DocumentHashMismatch;
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
    return error instanceof ServiceException
        ? (ServiceException) error
        : new ServiceException(mapErrorToServiceError(error));
  }

  public static ServiceException mapBrokerError(final BrokerError error) {
    return new ServiceException(mapBrokerErrorToServiceError(null, error));
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
      case FORBIDDEN -> new ServiceException(errorMessage, FORBIDDEN);
      case CONNECTION_FAILED -> {
        final String detail = "The search client could not connect to the search server";
        LOGGER.debug(detail, cse);
        yield new ServiceException(detail, UNAVAILABLE);
      }
      case SECONDARY_STORAGE_NOT_SET -> {
        final String detail =
            "The search client requires a secondary storage, but none is set. Secondary storage can be configured using the '"
                + PROPERTY_CAMUNDA_DATABASE_TYPE
                + "' property";
        LOGGER.debug(detail, cse);
        yield new ServiceException(detail, FORBIDDEN);
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
    return mapErrorToServiceError(error, error);
  }

  private static ServiceError mapErrorToServiceError(
      final Throwable rootError, final Throwable error) {

    return switch (error) {
      case final ExecutionException e -> mapErrorToServiceError(rootError, e.getCause());
      case final CompletionException e -> mapErrorToServiceError(rootError, e.getCause());
      case final ServiceException e -> new ServiceError(e.getMessage(), e.getStatus());
      case final BrokerErrorException brokerError ->
          mapBrokerErrorToServiceError(rootError, brokerError.getError());
      case final BrokerRejectionException rejection ->
          mapRejectionToServiceError(rejection.getRejection());
      case final TimeoutException ignored -> {
        final var message =
            "Expected to handle request, but request timed out between gateway and broker";
        LOGGER.debug(message, rootError);
        yield new ServiceError(message, DEADLINE_EXCEEDED);
      }
      case final MsgpackException ignored -> {
        final var message = "Expected to handle request, but messagepack property was invalid";
        LOGGER.debug(message, rootError);
        yield new ServiceError(message, INVALID_ARGUMENT);
      }
      case final io.camunda.zeebe.msgpack.spec.MsgpackException ignored -> {
        final var message = "Expected to handle request, but messagepack property was invalid";
        LOGGER.debug(message, rootError);
        yield new ServiceError(message, INVALID_ARGUMENT);
      }
      case final JsonParseException ignored -> {
        final var message = "Expected to handle request, but JSON property was invalid";
        LOGGER.debug(message, rootError);
        yield new ServiceError(message, INVALID_ARGUMENT);
      }
      case final InvalidTenantRequestException ignored -> {
        LOGGER.debug(error.getMessage(), rootError);
        yield new ServiceError(error.getMessage(), INVALID_ARGUMENT);
      }
      case final IllegalTenantRequestException ignored -> {
        LOGGER.debug(error.getMessage(), rootError);
        yield new ServiceError(error.getMessage(), UNAUTHORIZED);
      }
      case final IllegalArgumentException ignored -> {
        final var message = "Expected to handle request, but JSON property was invalid";
        LOGGER.debug(message, rootError);
        yield new ServiceError(message, INVALID_ARGUMENT);
      }
      case final PartitionNotFoundException ignored -> {
        final var message = "Expected to handle request, but request could not be delivered";
        LOGGER.debug(message, rootError);
        yield new ServiceError(message, UNAVAILABLE);
      }
      case final RequestRetriesExhaustedException ignored -> {
        final var message = "Expected to handle request, but all retries have been exhausted";
        // this error occurs when all partitions have exhausted for requests which have no fixed
        // partitions - it will then also occur when back pressure kicks in, leading to a large
        // burst
        // of error logs that is, in fact, expected
        LOGGER.trace(message, rootError);
        yield new ServiceError(message, RESOURCE_EXHAUSTED);
      }
      case final PartitionInactiveException ignored -> {
        final var message =
            "Expected to handle request, but the target partition is currently inactive";
        LOGGER.trace(message, rootError);
        yield new ServiceError(message, UNAVAILABLE);
      }
      case final NoTopologyAvailableException ignored -> {
        final var message =
            "Expected to handle request, but the gateway does not know any partitions yet";
        LOGGER.trace(message, rootError);
        yield new ServiceError(message, UNAVAILABLE);
      }
      case final ConnectTimeoutException ignored -> {
        final var message =
            "Expected to handle request, but a connection timeout exception occurred";
        LOGGER.warn(message, rootError);
        yield new ServiceError(message, UNAVAILABLE);
      }
      case final ConnectException ignored -> {
        final var message =
            "Expected to handle request, but there was a connection error with one of the brokers";
        LOGGER.warn(message, rootError);
        yield new ServiceError(message, UNAVAILABLE);
      }
      case final MessagingException.ConnectionClosed ignored -> {
        final var message =
            "Expected to handle request, but the connection was cut prematurely with the broker; "
                + "the request may or may not have been accepted, and may not be safe to retry.";
        LOGGER.warn(message, rootError);
        yield new ServiceError(message, ABORTED);
      }
      default -> {
        LOGGER.error("Expected to handle request, but an unexpected error occurred", rootError);
        yield new ServiceError(
            "Unexpected error occurred during the request processing: " + error.getMessage(),
            INTERNAL);
      }
    };
  }

  private static ServiceError mapBrokerErrorToServiceError(
      final Throwable rootError, final BrokerError error) {
    final String message = error.getMessage();

    return switch (error.getCode()) {
      case PROCESS_NOT_FOUND -> {
        LOGGER.trace("Entity was not found: {}", error, rootError);
        yield new ServiceError(message, NOT_FOUND);
      }
      case RESOURCE_EXHAUSTED -> {
        LOGGER.trace("Target broker is currently overloaded: {}", error, rootError);
        yield new ServiceError(message, RESOURCE_EXHAUSTED);
      }
      case PARTITION_LEADER_MISMATCH -> {
        // return UNAVAILABLE to indicate to the user that retrying might solve the issue, as this
        // is usually a transient issue
        LOGGER.trace("Target broker was not the leader of the partition: {}", error, rootError);
        yield new ServiceError(message, UNAVAILABLE);
      }
      case MALFORMED_REQUEST -> {
        LOGGER.debug("Malformed request: {}", message, rootError);
        yield new ServiceError(message, INVALID_ARGUMENT);
      }
      case PARTITION_UNAVAILABLE -> {
        LOGGER.debug("Partition is currently unavailable: {}", error, rootError);
        yield new ServiceError(message, UNAVAILABLE);
      }
      default -> {
        // all the following are for cases where retrying (with the same gateway) is not expected
        // to solve anything
        LOGGER.error(
            "Expected to handle request, but received an internal error from broker: {}",
            error,
            rootError);
        yield new ServiceError(
            String.format(
                "Unexpected error occurred between gateway and broker (code: %s) (message: %s)",
                error.getCode(), error.getMessage()),
            INTERNAL);
      }
    };
  }

  private static ServiceError mapRejectionToServiceError(final BrokerRejection rejection) {
    final String message =
        String.format(
            "Command '%s' rejected with code '%s': %s",
            rejection.intent(), rejection.type(), rejection.reason());
    LOGGER.trace("Expected to handle request, but the broker rejected it: {}", message);
    return switch (rejection.type()) {
      case INVALID_ARGUMENT -> new ServiceError(message, INVALID_ARGUMENT);
      case NOT_FOUND -> new ServiceError(message, NOT_FOUND);
      case ALREADY_EXISTS -> new ServiceError(message, ALREADY_EXISTS);
      case INVALID_STATE -> new ServiceError(message, INVALID_STATE);
      case PROCESSING_ERROR, EXCEEDED_BATCH_RECORD_SIZE -> new ServiceError(message, INTERNAL);
      case UNAUTHORIZED -> new ServiceError(message, UNAUTHORIZED);
      case FORBIDDEN -> new ServiceError(message, FORBIDDEN);
      default -> new ServiceError(message, UNKNOWN);
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
      case final DocumentHashMismatch dhm ->
          dhm.providedHash() == null || dhm.providedHash().isBlank()
              ? new ServiceError(
                  "No document hash provided for document %s".formatted(dhm.documentId()),
                  INVALID_ARGUMENT)
              : new ServiceError(
                  "Document hash for document %s doesn't match the provided hash %s"
                      .formatted(dhm.documentId(), dhm.providedHash()),
                  INVALID_ARGUMENT);
      default -> new ServiceError("Unexpected error occurred when handling document", INTERNAL);
    };
  }
}
