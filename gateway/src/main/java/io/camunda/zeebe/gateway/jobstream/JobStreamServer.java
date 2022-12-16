/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.jobstream;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.protocol.impl.encoding.PushedJobRequest;
import io.camunda.zeebe.scheduler.Actor;
import io.prometheus.client.Counter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;

public final class JobStreamServer extends Actor {
  private static final byte[] SUCCESS_RESPONSE = new byte[0];
  private static final Counter RECEIVED_JOBS =
      Counter.build()
          .namespace("job_stream")
          .name("gateway_received_job")
          .help("Total count of pushed jobs")
          .register();
  private static final Counter PUSHED_JOBS =
      Counter.build()
          .namespace("job_stream")
          .name("gateway_pushed_job")
          .help("Total count of pushed jobs")
          .register();
  private static final Counter PUSHED_JOB_ERRORS =
      Counter.build()
          .namespace("job_stream")
          .name("gateway_pushed_job_errors")
          .help("Total count of errors occurring when pushing a job")
          .register();
  private static final Counter DROPPED_JOBS =
      Counter.build()
          .namespace("job_stream")
          .name("gateway_dropped_job")
          .help("Total count of jobs lost due to missing client")
          .register();

  private final ClusterCommunicationService communicationService;

  private ServerStreamObserver<ActivatedJob> observer;

  public JobStreamServer(final ClusterCommunicationService communicationService) {
    this.communicationService = communicationService;
  }

  @Override
  protected void onActorStarted() {
    communicationService.subscribe(
        "job-stream-push", this::deserialize, this::handleRequest, Function.identity(), actor::run);
  }

  @Override
  protected void onActorClosing() {
    communicationService.unsubscribe("job-stream-push");
    if (observer != null) {
      CloseHelper.quietClose(() -> observer.onCompleted());
      observer = null;
    }
  }

  public void setObserver(final ServerStreamObserver<ActivatedJob> observer) {
    actor.run(() -> this.observer = observer);
  }

  private byte[] handleRequest(final PushedJobRequest request) {
    RECEIVED_JOBS.inc();

    if (observer == null) {
      DROPPED_JOBS.inc();
      throw new UnsupportedOperationException(
          "No registered observer/client; job [%s] will be lost".formatted(request));
    }

    final var job = ResponseMapper.toActivatedJobResponse(request.key(), request.job());
    try {
      observer.onNext(job);
      PUSHED_JOBS.inc();
    } catch (final Exception e) {
      observer.onError(e);
      PUSHED_JOB_ERRORS.inc();
      throw new UncheckedIOException(
          new IOException(
              "Failed to forward job [%s] to client, job will be lost".formatted(request), e));
    }

    return SUCCESS_RESPONSE;
  }

  private PushedJobRequest deserialize(final byte[] serialized) {
    final var request = new PushedJobRequest();
    request.wrap(new UnsafeBuffer(serialized), 0, serialized.length);

    return request;
  }
}
