/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport;

import static io.camunda.zeebe.util.StringUtil.getBytes;
import static java.lang.String.format;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ErrorResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.ServerResponseImpl;
import io.camunda.zeebe.util.EnsureUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Arrays;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;

public final class ErrorResponseWriter implements BufferWriter {
  public static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private static final String UNSUPPORTED_MESSAGE_FORMAT =
      "Expected to handle only messages of type %s, but received one of type '%s'";
  private static final String PARTITION_LEADER_MISMATCH_FORMAT =
      "Expected to handle client message on the leader of partition '%d', but this node is not the leader for it";
  private static final String MALFORMED_REQUEST_FORMAT =
      "Expected to handle client message, but could not read it: %s";
  private static final String INVALID_CLIENT_VERSION_FORMAT =
      "Expected client to have protocol version less than or equal to '%d', but was '%d'";
  private static final String INVALID_MESSAGE_TEMPLATE_FORMAT =
      "Expected to handle only messages with template IDs of %s, but received one with id '%d'";
  private static final String INVALID_DEPLOYMENT_PARTITION_FORMAT =
      "Expected to deploy processes to partition '%d', but was attempted on partition '%d'";
  private static final String PROCESS_NOT_FOUND_FORMAT =
      "Expected to get process with %s, but no such process found";
  private static final String RESOURCE_EXHAUSTED = "Reached maximum capacity of requests handled";
  private static final String PARTITION_UNAVAILABLE = "Cannot accept requests for partition %d.";
  private static final String OUT_OF_DISK_SPACE =
      "Cannot accept requests for partition %d. Broker is out of disk space";

  private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
  private final ErrorResponseEncoder errorResponseEncoder = new ErrorResponseEncoder();
  private final ServerOutput output;
  private final ServerResponseImpl response = new ServerResponseImpl();
  private ErrorCode errorCode;
  private byte[] errorMessage;

  public ErrorResponseWriter() {
    this(null);
  }

  public ErrorResponseWriter(final ServerOutput output) {
    this.output = output;
  }

  public <T> ErrorResponseWriter unsupportedMessage(final T actualType, final T... expectedTypes) {
    return errorCode(ErrorCode.UNSUPPORTED_MESSAGE)
        .errorMessage(
            String.format(UNSUPPORTED_MESSAGE_FORMAT, Arrays.toString(expectedTypes), actualType));
  }

  public ErrorResponseWriter partitionLeaderMismatch(final int partitionId) {
    return errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
        .errorMessage(String.format(PARTITION_LEADER_MISMATCH_FORMAT, partitionId));
  }

  public ErrorResponseWriter invalidClientVersion(
      final int maximumVersion, final int clientVersion) {
    return errorCode(ErrorCode.INVALID_CLIENT_VERSION)
        .errorMessage(String.format(INVALID_CLIENT_VERSION_FORMAT, maximumVersion, clientVersion));
  }

  public ErrorResponseWriter internalError(final String message, final Object... args) {
    return errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(String.format(message, args));
  }

  public ErrorResponseWriter resourceExhausted() {
    return errorCode(ErrorCode.RESOURCE_EXHAUSTED).errorMessage(RESOURCE_EXHAUSTED);
  }

  public ErrorResponseWriter resourceExhausted(final String message) {
    return errorCode(ErrorCode.RESOURCE_EXHAUSTED).errorMessage(message);
  }

  public ErrorResponseWriter partitionUnavailable(final int partitionId) {
    return errorCode(ErrorCode.PARTITION_UNAVAILABLE)
        .errorMessage(String.format(PARTITION_UNAVAILABLE, partitionId));
  }

  public ErrorResponseWriter partitionUnavailable(final String message) {
    return errorCode(ErrorCode.PARTITION_UNAVAILABLE).errorMessage(message);
  }

  public ErrorResponseWriter outOfDiskSpace(final int partitionId) {
    return errorCode(ErrorCode.RESOURCE_EXHAUSTED)
        .errorMessage(String.format(OUT_OF_DISK_SPACE, partitionId));
  }

  public ErrorResponseWriter malformedRequest(Throwable e) {
    final StringBuilder builder = new StringBuilder();

    do {
      builder.append(e.getMessage()).append("; ");
      e = e.getCause();
    } while (e != null);

    return errorCode(ErrorCode.MALFORMED_REQUEST)
        .errorMessage(String.format(MALFORMED_REQUEST_FORMAT, builder.toString()));
  }

  public ErrorResponseWriter invalidMessageTemplate(
      final int actualTemplateId, final int... expectedTemplates) {
    return errorCode(ErrorCode.INVALID_MESSAGE_TEMPLATE)
        .errorMessage(
            INVALID_MESSAGE_TEMPLATE_FORMAT, Arrays.toString(expectedTemplates), actualTemplateId);
  }

  public ErrorResponseWriter invalidDeploymentPartition(
      final int expectedPartitionId, final int actualPartitionId) {
    return errorCode(ErrorCode.INVALID_DEPLOYMENT_PARTITION)
        .errorMessage(
            String.format(
                INVALID_DEPLOYMENT_PARTITION_FORMAT, expectedPartitionId, actualPartitionId));
  }

  public ErrorResponseWriter processNotFound(final String processIdentifier) {
    return errorCode(ErrorCode.PROCESS_NOT_FOUND)
        .errorMessage(String.format(PROCESS_NOT_FOUND_FORMAT, processIdentifier));
  }

  public ErrorResponseWriter mapWriteError(final int partitionId, final WriteFailure error) {
    return switch (error) {
      case CLOSED ->
          errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
              .errorMessage(
                  ("Expected to handle client message on the leader of partition '%d',"
                          + " but the writer is closed. Most likely, this node is not the"
                          + " leader for this partition.")
                      .formatted(partitionId));
      case WRITE_LIMIT_EXHAUSTED ->
          resourceExhausted(
              String.format(
                  "Failed to write client request to partition '%d', because the write limit is exhausted.",
                  partitionId));
      case REQUEST_LIMIT_EXHAUSTED ->
          resourceExhausted(
              String.format(
                  "Failed to write client request to partition '%d', because the request limit is exhausted.",
                  partitionId));
      case INVALID_ARGUMENT -> raiseInternalError("due to invalid entry.", partitionId);
    };
  }

  private ErrorResponseWriter raiseInternalError(final String reason, final int partitionId) {
    final String message =
        "Failed to write client request to partition '%d', %s".formatted(partitionId, reason);
    LOG.debug(message);
    return internalError(message);
  }

  public ErrorResponseWriter errorCode(final ErrorCode errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public ErrorResponseWriter errorMessage(final String errorMessage) {
    this.errorMessage = getBytes(errorMessage);
    return this;
  }

  public ErrorResponseWriter errorMessage(final String errorMessage, final Object... args) {
    this.errorMessage = getBytes(format(errorMessage, args));
    return this;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public byte[] getErrorMessage() {
    return errorMessage;
  }

  public void tryWriteResponseOrLogFailure(
      final ServerOutput output, final int streamId, final long requestId) {
    tryWriteResponse(output, streamId, requestId);
  }

  public void tryWriteResponseOrLogFailure(final int streamId, final long requestId) {
    tryWriteResponseOrLogFailure(output, streamId, requestId);
  }

  public void tryWriteResponse(
      final ServerOutput output, final int streamId, final long requestId) {
    EnsureUtil.ensureNotNull("error code", errorCode);
    EnsureUtil.ensureNotNull("error message", errorMessage);

    try {
      response.reset().setPartitionId(streamId).writer(this).setRequestId(requestId);

      output.sendResponse(response);
    } finally {
      reset();
    }
  }

  public void tryWriteResponse(final int streamId, final long requestId) {
    tryWriteResponse(output, streamId, requestId);
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ErrorResponseEncoder.BLOCK_LENGTH
        + ErrorResponseEncoder.errorDataHeaderLength()
        + errorMessage.length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    final int initialOffset = offset;
    // protocol header
    messageHeaderEncoder.wrap(buffer, offset);

    messageHeaderEncoder
        .blockLength(errorResponseEncoder.sbeBlockLength())
        .templateId(errorResponseEncoder.sbeTemplateId())
        .schemaId(errorResponseEncoder.sbeSchemaId())
        .version(errorResponseEncoder.sbeSchemaVersion());

    offset += messageHeaderEncoder.encodedLength();

    // error message
    errorResponseEncoder.wrap(buffer, offset);

    errorResponseEncoder.errorCode(errorCode).putErrorData(errorMessage, 0, errorMessage.length);

    return errorResponseEncoder.limit() - initialOffset;
  }

  public void reset() {
    errorCode = null;
    errorMessage = null;
  }
}
