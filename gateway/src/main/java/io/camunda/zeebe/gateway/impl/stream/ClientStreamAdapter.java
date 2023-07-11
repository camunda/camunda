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
import io.grpc.stub.ServerCallStreamObserver;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;

public class ClientStreamAdapter {
  private final ClientStreamer<JobActivationProperties> jobStreamer;

  public ClientStreamAdapter(final ClientStreamer<JobActivationProperties> jobStreamer) {
    this.jobStreamer = jobStreamer;
  }

  public void handle(
      final StreamActivatedJobsRequest request,
      final ServerCallStreamObserver<ActivatedJob> responseObserver) {
    final JobActivationProperties jobActivationProperties = toJobActivationProperties(request);

    final ActorFuture<ClientStreamId> clientStreamId =
        jobStreamer.add(
            wrapString(request.getType()),
            jobActivationProperties,
            new ClientStreamConsumerImpl(responseObserver));

    final var removeJobStream = new JobStreamRemover(clientStreamId, jobStreamer);
    responseObserver.setOnCloseHandler(removeJobStream);
    responseObserver.setOnCancelHandler(removeJobStream);
  }

  static class ClientStreamConsumerImpl implements ClientStreamConsumer {
    private final ServerCallStreamObserver<ActivatedJob> responseObserver;

    public ClientStreamConsumerImpl(final ServerCallStreamObserver<ActivatedJob> responseObserver) {
      this.responseObserver = responseObserver;
    }

    @Override
    public CompletableFuture<Void> push(final DirectBuffer payload) {
      // TODO use gRPCExecutor thread pool
      return CompletableFuture.runAsync(
          () -> {
            final ActivatedJobImpl deserializedJob = new ActivatedJobImpl();
            deserializedJob.wrap(payload);
            final ActivatedJob activatedJob = ResponseMapper.toActivatedJob(deserializedJob);

            try {
              responseObserver.onNext(activatedJob);
            } catch (final Exception e) {
              responseObserver.onError(e);
              CompletableFuture.failedFuture(e);
            }
          });
    }
  }

  private record JobStreamRemover(
      ActorFuture<ClientStreamId> clientStreamId,
      ClientStreamer<JobActivationProperties> jobStreamer)
      implements Runnable {

    @Override
    public void run() {
      clientStreamId.onComplete((streamId, throwable) -> jobStreamer.remove(streamId));
    }
  }
}
