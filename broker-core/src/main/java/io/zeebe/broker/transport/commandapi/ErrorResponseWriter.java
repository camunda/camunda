/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import static io.zeebe.util.StringUtil.getBytes;
import static java.lang.String.format;

import io.zeebe.broker.Loggers;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.protocol.record.ErrorResponseEncoder;
import io.zeebe.protocol.record.MessageHeaderEncoder;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.EnsureUtil;
import io.zeebe.util.buffer.BufferWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;

public class ErrorResponseWriter implements BufferWriter {
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
      "Expected to deploy workflows to partition '%d', but was attempted on partition '%d'";
  private static final String WORKFLOW_NOT_FOUND_FORMAT =
      "Expected to get workflow with %s, but no such workflow found";

  protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
  protected final ErrorResponseEncoder errorResponseEncoder = new ErrorResponseEncoder();
  protected final ServerOutput output;
  protected final ServerResponse response = new ServerResponse();
  protected ErrorCode errorCode;
  protected byte[] errorMessage;

  public ErrorResponseWriter() {
    this(null);
  }

  public ErrorResponseWriter(ServerOutput output) {
    this.output = output;
  }

  public <T> ErrorResponseWriter unsupportedMessage(String actualType, T... expectedTypes) {
    return this.errorCode(ErrorCode.UNSUPPORTED_MESSAGE)
        .errorMessage(
            String.format(UNSUPPORTED_MESSAGE_FORMAT, Arrays.toString(expectedTypes), actualType));
  }

  public ErrorResponseWriter partitionLeaderMismatch(int partitionId) {
    return errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
        .errorMessage(String.format(PARTITION_LEADER_MISMATCH_FORMAT, partitionId));
  }

  public ErrorResponseWriter invalidClientVersion(int maximumVersion, int clientVersion) {
    return errorCode(ErrorCode.INVALID_CLIENT_VERSION)
        .errorMessage(String.format(INVALID_CLIENT_VERSION_FORMAT, maximumVersion, clientVersion));
  }

  public ErrorResponseWriter internalError(String message, Object... args) {
    return errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(String.format(message, args));
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
      int actualTemplateId, int... expectedTemplates) {
    return errorCode(ErrorCode.INVALID_MESSAGE_TEMPLATE)
        .errorMessage(
            INVALID_MESSAGE_TEMPLATE_FORMAT, Arrays.toString(expectedTemplates), actualTemplateId);
  }

  public ErrorResponseWriter invalidDeploymentPartition(
      int expectedPartitionId, int actualPartitionId) {
    return errorCode(ErrorCode.INVALID_DEPLOYMENT_PARTITION)
        .errorMessage(
            String.format(
                INVALID_DEPLOYMENT_PARTITION_FORMAT, expectedPartitionId, actualPartitionId));
  }

  public ErrorResponseWriter workflowNotFound(String workflowIdentifier) {
    return errorCode(ErrorCode.WORKFLOW_NOT_FOUND)
        .errorMessage(String.format(WORKFLOW_NOT_FOUND_FORMAT, workflowIdentifier));
  }

  public ErrorResponseWriter errorCode(ErrorCode errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public ErrorResponseWriter errorMessage(String errorMessage) {
    this.errorMessage = getBytes(errorMessage);
    return this;
  }

  public ErrorResponseWriter errorMessage(String errorMessage, Object... args) {
    this.errorMessage = getBytes(format(errorMessage, args));
    return this;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public byte[] getErrorMessage() {
    return errorMessage;
  }

  public boolean tryWriteResponseOrLogFailure(ServerOutput output, int streamId, long requestId) {
    final boolean isWritten = tryWriteResponse(output, streamId, requestId);

    if (!isWritten) {
      LOG.error(
          "Failed to write error response. Error code: '{}', error message: '{}'",
          errorCode != null ? errorCode.name() : ErrorCode.NULL_VAL.name(),
          new String(errorMessage, StandardCharsets.UTF_8));
    }

    return isWritten;
  }

  public boolean tryWriteResponseOrLogFailure(int streamId, long requestId) {
    return tryWriteResponseOrLogFailure(this.output, streamId, requestId);
  }

  public boolean tryWriteResponse(ServerOutput output, int streamId, long requestId) {
    EnsureUtil.ensureNotNull("error code", errorCode);
    EnsureUtil.ensureNotNull("error message", errorMessage);

    try {
      response.reset().remoteStreamId(streamId).writer(this).requestId(requestId);

      return output.sendResponse(response);
    } finally {
      reset();
    }
  }

  public boolean tryWriteResponse(int streamId, long requestId) {
    return tryWriteResponse(this.output, streamId, requestId);
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ErrorResponseEncoder.BLOCK_LENGTH
        + ErrorResponseEncoder.errorDataHeaderLength()
        + errorMessage.length;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
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
  }

  public void reset() {
    errorCode = null;
    errorMessage = null;
  }
}
