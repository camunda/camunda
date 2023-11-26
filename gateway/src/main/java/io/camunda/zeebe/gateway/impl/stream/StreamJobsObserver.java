/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.stream;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamJobsControl;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamJobsControl.MessageCase;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamJobsControl.Registration;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamBlockedException;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StreamJobsObserver
    implements StreamObserver<StreamJobsControl>, ClientStreamConsumer {
  private static final Logger LOGGER = LoggerFactory.getLogger(StreamJobsObserver.class);

  private final ConcurrencyControl executor;
  private final ClientStreamer<JobActivationProperties> jobStreamer;
  private final ServerCallStreamObserver<ActivatedJob> clientStream;

  private ClientStreamId streamId;
  private boolean isClosed;

  public StreamJobsObserver(
      final ConcurrencyControl executor,
      final ClientStreamer<JobActivationProperties> jobStreamer,
      final ServerCallStreamObserver<ActivatedJob> clientStream) {
    this.executor = executor;
    this.jobStreamer = jobStreamer;
    this.clientStream = clientStream;
  }

  @Override
  public void onNext(final StreamJobsControl message) {
    executor.execute(() -> handleControlMessage(message));
  }

  @Override
  public void onError(final Throwable t) {
    executor.run(() -> handleClientError(t));
  }

  @Override
  public void onCompleted() {
    executor.run(this::close);
  }

  @Override
  public ActorFuture<Void> push(final DirectBuffer payload) {
    final var result = new CompletableActorFuture<Void>();
    try {
      executor.run(() -> handlePushedJob(payload, result));
    } catch (final Exception e) {
      // only possible failure here is that the actor is not running, so close the stream
      // preemptively
      clientStream.onError(e);
      result.completeExceptionally(e);
    }

    return result;
  }

  private void handlePushedJob(
      final DirectBuffer payload, final CompletableActorFuture<Void> result) {
    final var deserializedJob = new ActivatedJobImpl();
    final ActivatedJob activatedJob;

    if (!clientStream.isReady()) {
      result.completeExceptionally(
          new ClientStreamBlockedException(
              "Expected to push payload (size = '%d') to stream, but stream is blocked"
                  .formatted(payload.capacity())));
      return;
    }

    // fail push on serialization errors, but no need to close the client stream
    try {
      deserializedJob.wrap(payload);
      activatedJob = ResponseMapper.toActivatedJob(deserializedJob);
    } catch (final Exception e) {
      result.completeExceptionally(e);
      return;
    }

    try {
      clientStream.onNext(activatedJob);
      result.complete(null);
    } catch (final Exception e) {
      clientStream.onError(e);
      result.completeExceptionally(e);
    }
  }

  private void handleClientError(final Throwable error) {
    // TODO: maybe don't log all errors
    LOGGER.warn("Received error from client job stream {}", streamId, error);
    close();
  }

  private void close() {
    isClosed = true;

    if (streamId != null) {
      jobStreamer.remove(streamId);
    }
  }

  private void handleControlMessage(final StreamJobsControl message) {
    if (message.getMessageCase() == MessageCase.REGISTRATION) {
      handleRegistration(message.getRegistration());
    } else {
      clientStream.onError(Status.INVALID_ARGUMENT.asRuntimeException());
      close();
    }
  }

  private void handleRegistration(final Registration message) {
    if (streamId != null) {
      clientStream.onError(Status.FAILED_PRECONDITION.asRuntimeException());
      close();
      return;
    }

    registerStream(message);
  }

  private void registerStream(final Registration request) {
    final var jobType = request.getType();
    final JobActivationProperties properties;
    try {
      properties = RequestMapper.toJobActivationProperties(request);
    } catch (final Exception e) {
      clientStream.onError(
          Status.INVALID_ARGUMENT
              .withCause(e)
              .withDescription("Failed to parse job activation properties")
              .augmentDescription("Cause: " + e.getMessage())
              .asRuntimeException());
      LangUtil.rethrowUnchecked(e);
      return;
    }

    if (jobType.isBlank()) {
      handleError(clientStream, "type", "present", "blank");
      return;
    }

    if (properties.timeout() < 1) {
      handleError(
          clientStream, "timeout", "greater than zero", Long.toString(properties.timeout()));
      return;
    }

    executor.runOnCompletion(
        jobStreamer.add(wrapString(jobType), properties, this), this::onStreamAdded);
  }

  private void onStreamAdded(final ClientStreamId streamId, final Throwable error) {
    // the only possible reason it would fail is due to the actor being closed, meaning we would be
    // shutting down or a fatal error occurred; in either case, retrying would do no good
    if (error != null) {
      LOGGER.warn("Failed to register new job stream", error);
      clientStream.onError(
          Status.UNAVAILABLE
              .withDescription("Failed to register new job stream")
              .withCause(error)
              .augmentDescription("Cause: " + error.getMessage())
              .asRuntimeException());
      return;
    }

    if (isClosed) {
      LOGGER.warn("Stream is already closed, removing...");
      jobStreamer.remove(streamId);
      return;
    }

    this.streamId = streamId;
  }

  private void handleError(
      final ServerCallStreamObserver<GatewayOuterClass.ActivatedJob> responseObserver,
      final String field,
      final String expectation,
      final String actual) {
    final var format = "Expected to stream activated jobs with %s to be %s, but it was %s";
    final var errorMessage = format.formatted(field, expectation, actual);

    isClosed = true;
    responseObserver.onError(
        new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription(errorMessage)));
  }
}
