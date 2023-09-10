/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.stream;

import static io.camunda.zeebe.gateway.RequestMapper.toJobActivationProperties;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.LockUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.jcip.annotations.GuardedBy;
import org.agrona.DirectBuffer;

public class ClientStreamAdapter {
  private final ClientStreamer<JobActivationProperties> jobStreamer;
  private final ActorSchedulingService scheduler;
  private final Executor executor;

  public ClientStreamAdapter(
      final ClientStreamer<JobActivationProperties> jobStreamer,
      final ActorSchedulingService scheduler,
      final Executor executor) {
    this.jobStreamer = jobStreamer;
    this.scheduler = scheduler;
    this.executor = executor;
  }

  public void handle(
      final StreamActivatedJobsRequest request,
      final ServerCallStreamObserver<ActivatedJob> responseObserver) {
    if (request.getType().isBlank()) {
      handleError(responseObserver, "type", "present", "blank");
      return;
    }
    if (request.getTimeout() < 1) {
      handleError(
          responseObserver, "timeout", "greater than zero", Long.toString(request.getTimeout()));
      return;
    }

    handleInternal(request, responseObserver);
  }

  private void handleInternal(
      final StreamActivatedJobsRequest request,
      final ServerCallStreamObserver<ActivatedJob> responseObserver) {
    final var jobActivationProperties = toJobActivationProperties(request);
    final var streamType = wrapString(request.getType());
    final var consumer = new ClientStreamConsumerImpl(responseObserver);
    final var cleaner = new AsyncJobStreamRemover(jobStreamer);

    // setting the handlers has to be done before the call is started, so we cannot do it in the
    // actor callbacks, which is why the remover can handle being called out of order
    responseObserver.setOnCloseHandler(cleaner);
    responseObserver.setOnCancelHandler(cleaner);

    scheduler
        .submitActor(consumer)
        .onComplete(
            (ok, error) -> {
              cleaner.onActorStarted(consumer);
              jobStreamer
                  .add(streamType, jobActivationProperties, consumer)
                  .onComplete(cleaner::onStreamAdded, executor);
            },
            executor);
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

  @VisibleForTesting("Allow unit testing behavior job handling behavior")
  static final class ClientStreamConsumerImpl extends Actor implements ClientStreamConsumer {
    private final StreamObserver<ActivatedJob> responseObserver;

    ClientStreamConsumerImpl(final StreamObserver<ActivatedJob> responseObserver) {
      this.responseObserver = responseObserver;
    }

    @Override
    public ActorFuture<Void> push(final DirectBuffer payload) {
      return actor.call(() -> handlePushedJob(payload));
    }

    private void handlePushedJob(final DirectBuffer payload) {
      final ActivatedJobImpl deserializedJob = new ActivatedJobImpl();
      deserializedJob.wrap(payload);
      final ActivatedJob activatedJob = ResponseMapper.toActivatedJob(deserializedJob);
      try {
        responseObserver.onNext(activatedJob);
      } catch (final Exception e) {
        responseObserver.onError(e);
        throw e;
      }
    }

    @Override
    protected void onActorClosing() {
      responseObserver.onCompleted();
    }

    @Override
    protected void handleFailure(final Throwable failure) {
      responseObserver.onError(failure);
    }
  }

  private static final class AsyncJobStreamRemover implements Runnable {
    private final Lock lock = new ReentrantLock();
    private final ClientStreamer<JobActivationProperties> jobStreamer;

    @GuardedBy("lock")
    private boolean isRemoved;

    @GuardedBy("lock")
    private ClientStreamId streamId;

    @GuardedBy("lock")
    private ClientStreamConsumerImpl consumer;

    private AsyncJobStreamRemover(final ClientStreamer<JobActivationProperties> jobStreamer) {
      this.jobStreamer = jobStreamer;
    }

    @Override
    public void run() {
      LockUtil.withLock(lock, this::lockedRemove);
    }

    private void onStreamAdded(final ClientStreamId streamId, final Throwable error) {
      LockUtil.withLock(lock, () -> lockedOnStreamAdded(streamId));
    }

    private void onActorStarted(final ClientStreamConsumerImpl consumer) {
      LockUtil.withLock(lock, () -> lockedOnActorStarted(consumer));
    }

    @GuardedBy("lock")
    private void lockedOnActorStarted(final ClientStreamConsumerImpl consumer) {
      if (isRemoved) {
        consumer.closeAsync();
        return;
      }

      this.consumer = consumer;
    }

    @GuardedBy("lock")
    private void lockedRemove() {
      isRemoved = true;

      if (streamId != null) {
        jobStreamer.remove(streamId);
      }

      if (consumer != null) {
        consumer.closeAsync();
      }
    }

    @GuardedBy("lock")
    private void lockedOnStreamAdded(final ClientStreamId streamId) {
      if (isRemoved) {
        jobStreamer.remove(streamId);
        return;
      }

      this.streamId = streamId;
    }
  }
}
