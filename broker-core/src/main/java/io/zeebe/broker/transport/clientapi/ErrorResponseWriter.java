/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.transport.clientapi;

import static io.zeebe.util.StringUtil.getBytes;
import static java.lang.String.format;

import io.zeebe.broker.Loggers;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
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

  protected ErrorCode errorCode;
  protected byte[] errorMessage;

  protected final ServerOutput output;
  protected final ServerResponse response = new ServerResponse();

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

  public void reset() {
    errorCode = null;
    errorMessage = null;
  }
}
