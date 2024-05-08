/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.stream;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamBlockedException;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.VisibleForTesting;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.Executor;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamJobsHandler extends Actor {
  private static final Logger LOGGER = LoggerFactory.getLogger(StreamJobsHandler.class);

  private final ClientStreamer<JobActivationProperties> jobStreamer;

  public StreamJobsHandler(final ClientStreamer<JobActivationProperties> jobStreamer) {
    this.jobStreamer = jobStreamer;
  }

  public void handle(
      final String jobType,
      final JobActivationProperties jobActivationProperties,
      final ServerCallStreamObserver<ActivatedJob> responseObserver) {
    // TODO(#14452): move validations to RequestMapper and convert
    //  to exceptions that can be used in the GrpcErrorMapper
    if (jobType.isBlank()) {
      handleError(responseObserver, "type", "present", "blank");
      return;
    }
    if (jobActivationProperties.timeout() < 1) {
      handleError(
          responseObserver,
          "timeout",
          "greater than zero",
          Long.toString(jobActivationProperties.timeout()));
      return;
    }

    handleInternal(jobType, jobActivationProperties, responseObserver);
  }

  private void handleInternal(
      final String jobType,
      final JobActivationProperties jobActivationProperties,
      final ServerCallStreamObserver<ActivatedJob> responseObserver) {
    final var streamType = wrapString(jobType);
    final var consumer = new JobStreamConsumer(responseObserver, actor);
    final var cleaner = new AsyncJobStreamRemover(jobStreamer, actor);

    // setting the handlers has to be done before the call is started, so we cannot do it in the
    // actor callbacks, which is why the remover can handle being called out of order
    responseObserver.setOnCloseHandler(cleaner);
    responseObserver.setOnCancelHandler(cleaner);

    actor.run(
        () ->
            actor.runOnCompletion(
                jobStreamer.add(streamType, jobActivationProperties, consumer),
                (streamId, error) -> onStreamAdded(responseObserver, cleaner, streamId, error)));
  }

  private void onStreamAdded(
      final StreamObserver<ActivatedJob> responseObserver,
      final AsyncJobStreamRemover cleaner,
      final ClientStreamId streamId,
      final Throwable error) {
    // the only possible reason it would fail is due to the actor being closed, meaning we would be
    // shutting down or a fatal error occurred; in either case, retrying would do no good
    if (error != null) {
      LOGGER.warn("Failed to register new job stream", error);
      responseObserver.onError(
          Status.UNAVAILABLE
              .withDescription("Failed to register new job stream")
              .withCause(error)
              .augmentDescription("Cause: " + error.getMessage())
              .asRuntimeException());
      return;
    }

    cleaner.streamId(streamId);
  }

  private void handleError(
      final ServerCallStreamObserver<ActivatedJob> responseObserver,
      final String field,
      final String expectation,
      final String actual) {
    final var format = "Expected to stream activated jobs with %s to be %s, but it was %s";
    final String errorMessage = format.formatted(field, expectation, actual);
    responseObserver.onError(
        new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription(errorMessage)));
  }

  @VisibleForTesting("Allow unit testing behavior")
  static final class JobStreamConsumer implements ClientStreamConsumer {
    private final ServerCallStreamObserver<ActivatedJob> responseObserver;
    private final ConcurrencyControl executor;

    @VisibleForTesting("Allow unit testing behavior")
    JobStreamConsumer(
        final ServerCallStreamObserver<ActivatedJob> responseObserver,
        final ConcurrencyControl executor) {
      this.responseObserver = responseObserver;
      this.executor = executor;
    }

    @Override
    public ActorFuture<Void> push(final DirectBuffer payload) {
      final var result = new CompletableActorFuture<Void>();
      try {
        executor.run(() -> handlePushedJob(payload, result));
      } catch (final Exception e) {
        // only possible failure here is that the actor is not running, so close the stream
        // preemptively
        responseObserver.onError(e);
        result.completeExceptionally(e);
      }

      return result;
    }

    private void handlePushedJob(
        final DirectBuffer payload, final CompletableActorFuture<Void> result) {
      final var deserializedJob = new ActivatedJobImpl();
      final ActivatedJob activatedJob;

      if (!responseObserver.isReady()) {
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
        responseObserver.onNext(activatedJob);
        result.complete(null);
      } catch (final Exception e) {
        responseObserver.onError(e);
        result.completeExceptionally(e);
      }
    }
  }

  @VisibleForTesting("Allow unit testing behavior")
  static final class AsyncJobStreamRemover implements Runnable {
    private final ClientStreamer<JobActivationProperties> jobStreamer;
    private final Executor executor;

    private boolean isRemoved;
    private ClientStreamId streamId;

    @VisibleForTesting("Allow unit testing behavior")
    AsyncJobStreamRemover(
        final ClientStreamer<JobActivationProperties> jobStreamer, final Executor executor) {
      this.jobStreamer = jobStreamer;
      this.executor = executor;
    }

    @Override
    public void run() {
      executor.execute(this::remove);
    }

    @VisibleForTesting("Allow unit testing behavior")
    void streamId(final ClientStreamId streamId) {
      executor.execute(() -> setStreamId(streamId));
    }

    private void remove() {
      isRemoved = true;

      if (streamId != null) {
        jobStreamer.remove(streamId);
      }
    }

    private void setStreamId(final ClientStreamId streamId) {
      if (isRemoved) {
        jobStreamer.remove(streamId);
        return;
      }

      this.streamId = streamId;
    }
  }
}
