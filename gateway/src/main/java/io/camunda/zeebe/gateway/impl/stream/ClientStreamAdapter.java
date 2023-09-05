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
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.VisibleForTesting;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.agrona.DirectBuffer;

public class ClientStreamAdapter {
  private final ClientStreamer<JobActivationProperties> jobStreamer;
  private final Executor executor;

  public ClientStreamAdapter(
      final ClientStreamer<JobActivationProperties> jobStreamer, final Executor executor) {
    this.jobStreamer = jobStreamer;
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
    final JobActivationProperties jobActivationProperties = toJobActivationProperties(request);

    final var futureId =
        jobStreamer.add(
            wrapString(request.getType()),
            jobActivationProperties,
            new ClientStreamConsumerImpl(responseObserver, executor));
    final var cleaner = new JobStreamRemover(futureId);
    responseObserver.setOnCloseHandler(cleaner);
    responseObserver.setOnCancelHandler(cleaner);
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
  static final class ClientStreamConsumerImpl implements ClientStreamConsumer {
    private final StreamObserver<ActivatedJob> responseObserver;
    private final Executor executor;

    public ClientStreamConsumerImpl(
        final StreamObserver<ActivatedJob> responseObserver, final Executor executor) {
      this.responseObserver = responseObserver;
      this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> push(final DirectBuffer payload) {
      return CompletableFuture.runAsync(() -> handlePushedJob(payload), executor);
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
  }

  private final class JobStreamRemover implements Runnable {
    private final ActorFuture<ClientStreamId> clientStreamId;

    private JobStreamRemover(final ActorFuture<ClientStreamId> clientStreamId) {
      this.clientStreamId = clientStreamId;
    }

    @Override
    public void run() {
      clientStreamId.onComplete(this::onJobStreamerId, executor);
    }

    private void onJobStreamerId(final ClientStreamId id, final Throwable error) {
      if (error != null) {
        return;
      }

      jobStreamer.remove(id);
    }
  }
}
