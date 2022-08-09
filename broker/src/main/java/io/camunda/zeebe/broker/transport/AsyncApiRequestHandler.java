/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.RequestReader;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.sbe.MessageDecoderFlyweight;
import org.slf4j.Logger;

/**
 * A {@link RequestHandler} that automatically decodes requests and encodes successful and error
 * responses. Handling requests is asynchronous, use {@link ApiRequestHandler} if handling can be
 * synchronous.
 *
 * @param <R> a {@link RequestReader} that reads the request
 * @param <W> a {@link ResponseWriter} that writes the response
 */
public abstract class AsyncApiRequestHandler<R extends RequestReader<?>, W extends ResponseWriter>
    extends Actor implements RequestHandler {

  public static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final Supplier<R> requestReaderSupplier;
  private final Supplier<W> responseWriterSupplier;

  private final Supplier<ErrorResponseWriter> errorResponseWriterSupplier =
      ErrorResponseWriter::new;

  protected AsyncApiRequestHandler(
      final Supplier<R> requestReaderSupplier, final Supplier<W> responseWriterSupplier) {
    this.requestReaderSupplier = requestReaderSupplier;
    this.responseWriterSupplier = responseWriterSupplier;
  }

  /**
   * Handles a request. The {@param requestReader} is populated at this point.
   *
   * @param partitionId the current partition id
   * @param requestId the current request id
   * @param requestReader a {@link R reader} that is already populated with the request data
   * @param responseWriter a {@link W writer} that can be used to write successful responses
   * @param errorWriter a {@link ErrorResponseWriter} that can be used to write error responses
   * @return a {@link ErrorResponseWriter} when the handling failed, or a {@link W ResponseWriter}
   *     when the handling was successful. Writes to the other writer will be ignored and are not
   *     sent as a response.
   */
  protected abstract ActorFuture<Either<ErrorResponseWriter, W>> handleAsync(
      int partitionId,
      long requestId,
      R requestReader,
      W responseWriter,
      ErrorResponseWriter errorWriter);

  @Override
  public final void onRequest(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    actor.submit(() -> handleRequest(serverOutput, partitionId, requestId, buffer, offset, length));
  }

  private void handleRequest(
      final ServerOutput serverOutput,
      final int partitionId,
      final long requestId,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    final var requestReader = requestReaderSupplier.get();
    final var responseWriter = responseWriterSupplier.get();
    final var errorResponseWriter = errorResponseWriterSupplier.get();

    requestReader.reset();
    responseWriter.reset();
    errorResponseWriter.reset();

    try {
      requestReader.wrap(buffer, offset, length);
    } catch (final RequestReaderException.InvalidTemplateException e) {
      errorResponseWriter
          .invalidMessageTemplate(e.actualTemplate, e.expectedTemplate)
          .tryWriteResponseOrLogFailure(serverOutput, partitionId, requestId);
      return;
    } catch (final Exception e) {
      LOG.error("Failed to deserialize message", e);
      errorResponseWriter
          .malformedRequest(e)
          .tryWriteResponseOrLogFailure(serverOutput, partitionId, requestId);
      return;
    }

    try {
      final var resultFuture =
          handleAsync(partitionId, requestId, requestReader, responseWriter, errorResponseWriter);
      resultFuture.onComplete(
          (result, throwable) -> {
            if (throwable != null) {
              LOG.error("Error handling request on partition {}", partitionId, throwable);
              errorResponseWriter
                  .internalError(
                      "Failed to handle request due to internal error; see the broker logs for more")
                  .tryWriteResponse(serverOutput, partitionId, requestId);
            }
            if (result.isLeft()) {
              result.getLeft().tryWriteResponse(serverOutput, partitionId, requestId);
            } else {
              result.get().tryWriteResponse(serverOutput, partitionId, requestId);
            }
          });
    } catch (final Exception e) {
      LOG.error("Error handling request on partition {}", partitionId, e);
      errorResponseWriter
          .internalError(
              "Failed to handle request due to internal error; see the broker logs for more")
          .tryWriteResponse(serverOutput, partitionId, requestId);
    }
  }

  /**
   * Extension of {@link BufferWriter} that provides extra methods used by {@link
   * AsyncApiRequestHandler} implementations
   */
  public interface ResponseWriter extends BufferWriter {

    /**
     * Writes a successful response to the {@link ServerOutput}
     *
     * @param output the underlying {@link ServerOutput} that can be written to.
     * @param partitionId the current partition id
     * @param requestId the current request id
     */
    void tryWriteResponse(final ServerOutput output, final int partitionId, final long requestId);

    /** Resets all internal state to prepare for sending the next response */
    void reset();
  }

  /**
   * Extension of {@link BufferReader} that provides extra methods used by {@link
   * AsyncApiRequestHandler} implementations.
   *
   * @param <T> the type of the message decoder.
   */
  public interface RequestReader<T extends MessageDecoderFlyweight> extends BufferReader {

    /** Reset all internal state to prepare for reading the next request. */
    void reset();

    /**
     * @return The message decoder which can be used by {@link AsyncApiRequestHandler}
     *     implementations to get access to the request data.
     */
    T getMessageDecoder();

    /**
     * @param buffer the buffer to read from
     * @param offset the offset at which to start reading
     * @param length the length of the values to read
     * @throws RequestReaderException if reading the request failed
     */
    @Override
    void wrap(DirectBuffer buffer, int offset, int length);
  }
}
