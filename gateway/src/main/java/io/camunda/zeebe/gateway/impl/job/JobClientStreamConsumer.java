/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.job;

import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class JobClientStreamConsumer implements ClientStreamConsumer {
  private static final Logger LOGGER = Loggers.JOB_STREAM_LOGGER;
  private final StreamObserver<ActivatedJob> observer;

  public JobClientStreamConsumer(final StreamObserver<ActivatedJob> observer) {
    this.observer = observer;
  }

  @Override
  public CompletableFuture<Void> push(final DirectBuffer payload) {
    return CompletableFuture.supplyAsync(() -> deserialize(payload))
        .thenApply(this::convertJob)
        .thenAccept(this::forwardJob);
  }

  private ActivatedJobImpl deserialize(final DirectBuffer payload) {
    final var deserialized = new ActivatedJobImpl();
    deserialized.wrap(payload);
    return deserialized;
  }

  private ActivatedJob convertJob(final ActivatedJobImpl pushedJob) {
    return ResponseMapper.toActivatedJobResponse(pushedJob.jobKey(), pushedJob.jobRecord());
  }

  private void forwardJob(final ActivatedJob job) {
    try {
      observer.onNext(job);
      LOGGER.trace("Pushed out job {}", job);
    } catch (final Exception e) {
      observer.onError(e);
      LOGGER.debug("Closing stream due to failure to stream job {}", job, e);
      throw e;
    }
  }
}
