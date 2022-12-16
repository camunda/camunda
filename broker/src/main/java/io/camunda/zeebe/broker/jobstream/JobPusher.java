/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.protocol.impl.encoding.PushedJobRequest;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.scheduler.Actor;
import io.prometheus.client.Counter;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class JobPusher extends Actor {
  private static final Logger LOGGER = Loggers.JOB_STREAM;
  private static final Counter PUSHED_JOBS =
      Counter.build()
          .namespace("job_stream")
          .name("broker_pushed_job")
          .help("Total count of pushed jobs")
          .register();
  private static final Counter PUSHED_JOB_ERRORS =
      Counter.build()
          .namespace("job_stream")
          .name("broker_pushed_job_errors")
          .help("Total count of errors occurring when pushing a job")
          .register();

  private final ClusterCommunicationService communicationService;
  private final MemberId gatewayMemberId;

  public JobPusher(
      final ClusterCommunicationService communicationService, final MemberId gatewayMemberId) {
    this.communicationService = communicationService;
    this.gatewayMemberId = gatewayMemberId;
  }

  public void push(final long key, final JobRecord job) {
    final var request = new PushedJobRequest().key(key).job(job);
    actor.run(() -> sendJob(request));
  }

  private void sendJob(final PushedJobRequest request) {
    final CompletableFuture<byte[]> response =
        communicationService.send(
            "job-stream-push",
            request,
            this::serializeRequest,
            Function.identity(),
            gatewayMemberId,
            Duration.ofSeconds(5));

    PUSHED_JOBS.inc();

    response.whenComplete(
        (payload, error) -> {
          if (error != null) {
            PUSHED_JOB_ERRORS.inc();
            LOGGER.warn("Failed to push activated job {}, job may be 'lost'", request, error);
          }
        });
  }

  private byte[] serializeRequest(final PushedJobRequest request) {
    final var writeBuffer = new UnsafeBuffer(ByteBuffer.allocate(request.getLength()));
    request.write(writeBuffer, 0);

    return writeBuffer.byteArray();
  }
}
