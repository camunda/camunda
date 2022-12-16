/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.protocol.impl.encoding.PushedJobRequest;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.scheduler.Actor;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class JobPusher extends Actor implements ClusterMembershipEventListener {
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
  private static final Gauge REGISTERED_GATEWAYS =
      Gauge.build()
          .namespace("job_stream")
          .name("registered_gateway")
          .help("Total count of registered gateway endpoints")
          .register();
  private static final Histogram PUSH_DURATION =
      Histogram.build()
          .namespace("job_stream")
          .labelNames("gateway")
          .name("broker_push_duration")
          .help("Approximate duration of broker-to-gateway push")
          .register();

  private final ClusterCommunicationService communicationService;
  private final ClusterEventService eventService;
  private final ClusterMembershipService membershipService;
  private final Set<MemberId> gateways = new HashSet<>();

  public JobPusher(final ClusterServices clusterServices) {
    communicationService = clusterServices.getCommunicationService();
    eventService = clusterServices.getEventService();
    membershipService = clusterServices.getMembershipService();
  }

  @Override
  protected void onActorStarted() {
    eventService.subscribe("job-stream-register", (Consumer<String>) this::addGateway, actor::run);
    eventService.subscribe(
        "job-stream-unregister", (Consumer<String>) this::removeGateway, actor::run);
    membershipService.addListener(this);
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    final var isGateway = "true".equals(event.subject().properties().getProperty("isGateway"));
    if (!isGateway) {
      return;
    }

    if (event.type() == Type.MEMBER_ADDED) {
      actor.run(() -> addGateway(event.subject().id()));
    } else if (event.type() == Type.MEMBER_REMOVED) {
      actor.run(() -> removeGateway(event.subject().id()));
    }
  }

  private void removeGateway(final String gatewayId) {
    final var memberId = MemberId.from(gatewayId);
    removeGateway(memberId);
  }

  private void removeGateway(final MemberId memberId) {
    if (gateways.remove(memberId)) {
      LOGGER.debug("Removed new gateway {} for pushing", memberId);
      REGISTERED_GATEWAYS.dec();
    }
  }

  private void addGateway(final String gatewayId) {
    final var memberId = MemberId.from(gatewayId);
    addGateway(memberId);
  }

  private void addGateway(final MemberId memberId) {
    if (gateways.add(memberId)) {
      LOGGER.debug("Registered new gateway {} for pushing", memberId);
      REGISTERED_GATEWAYS.inc();
    }
  }

  public void push(final long key, final JobRecord job) {
    final var request = new PushedJobRequest().key(key).job(job);
    actor.run(() -> sendJob(request));
  }

  private void sendJob(final PushedJobRequest request) {
    // shuffle gateways for easy load balancing
    final var sortedGateways = new ArrayList<>(gateways);
    Collections.shuffle(sortedGateways);

    final var targetGateway = sortedGateways.get(0);
    final var timer = PUSH_DURATION.labels(targetGateway.id()).startTimer();
    final CompletableFuture<byte[]> response =
        communicationService.send(
            "job-stream-push",
            request,
            this::serializeRequest,
            Function.identity(),
            targetGateway,
            Duration.ofSeconds(5));
    PUSHED_JOBS.inc();

    response.whenComplete(
        (payload, error) -> {
          timer.close();
          if (error != null) {
            PUSHED_JOB_ERRORS.inc();
            LOGGER.error("Failed to push activated job {}, job may be 'lost'", request, error);
          }
        });
  }

  private byte[] serializeRequest(final PushedJobRequest request) {
    final var writeBuffer = new UnsafeBuffer(ByteBuffer.allocate(request.getLength()));
    request.write(writeBuffer, 0);

    return writeBuffer.byteArray();
  }
}
