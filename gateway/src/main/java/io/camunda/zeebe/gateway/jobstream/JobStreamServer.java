/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.jobstream;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.protocol.impl.encoding.PushedJobRequest;
import io.camunda.zeebe.scheduler.Actor;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;

public final class JobStreamServer extends Actor implements ClusterMembershipEventListener {
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
  private static final Gauge REGISTERED_CLIENTS =
      Gauge.build()
          .namespace("job_stream")
          .name("registered_client")
          .help("Total count of registered client observers")
          .register();
  private static final Histogram PUSH_DURATION =
      Histogram.build()
          .namespace("job_stream")
          .name("gateway_push_duration")
          .help("Approximate duration of gateway-to-client push")
          .register();

  private final ClusterCommunicationService communicationService;
  private final ClusterEventService eventService;
  private final ClusterMembershipService membershipService;
  private final List<ServerStreamObserver<ActivatedJob>> observers = new ArrayList<>();

  public JobStreamServer(
      final ClusterCommunicationService communicationService,
      final ClusterEventService eventService,
      final ClusterMembershipService membershipService) {
    this.communicationService = communicationService;
    this.eventService = eventService;
    this.membershipService = membershipService;
  }

  @Override
  protected void onActorStarted() {
    REGISTERED_CLIENTS.set(0);
    communicationService.subscribe(
        "job-stream-push", this::deserialize, this::handleRequest, Function.identity(), actor::run);
    membershipService.addListener(this);
  }

  @Override
  protected void onActorClosing() {
    communicationService.unsubscribe("job-stream-push");
    membershipService.removeListener(this);
    notifyBrokersToRemoveStreamReceiver();

    observers.forEach(observer -> CloseHelper.quietClose(observer::onCompleted));
    observers.clear();
    REGISTERED_CLIENTS.set(0);
  }

  private void notifyBrokersToRemoveStreamReceiver() {
    eventService.broadcast("job-stream-unregister", membershipService.getLocalMember().id().id());
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    final var isBroker = "true".equals(event.subject().properties().getProperty("isBroker"));
    if (!isBroker) {
      return;
    }

    if (event.type() == Type.MEMBER_ADDED) {
      // notify that we exist
      if (!observers.isEmpty()) {
        notifyBrokersAboutStreamReceiver();
      }
    }
  }

  private void notifyBrokersAboutStreamReceiver() {
    eventService.broadcast("job-stream-register", membershipService.getLocalMember().id().id());
  }

  public void asyncAddObserver(final ServerStreamObserver<ActivatedJob> observer) {
    Loggers.JOB_STREAM.debug("Registering new stream observer for job pushing");
    actor.run(() -> addObserver(observer));
  }

  private void addObserver(final ServerStreamObserver<ActivatedJob> observer) {
    if (observers.isEmpty()) {
      notifyBrokersAboutStreamReceiver();
    }

    observers.add(observer);
    REGISTERED_CLIENTS.set(observers.size());
    Loggers.JOB_STREAM.info("Add stream observer for job pushing");
  }

  public void asyncRemoveObserver(final ServerStreamObserver<ActivatedJob> observer) {
    Loggers.JOB_STREAM.debug("Remove stream observer for job pushing");
    actor.run(() -> removeObserver(observer));
  }

  private void removeObserver(final ServerStreamObserver<ActivatedJob> observer) {
    observers.remove(observer);
    Loggers.JOB_STREAM.info("Removing stream observer for job pushing");

    if (observers.isEmpty()) {
      notifyBrokersToRemoveStreamReceiver();
    }
  }

  private byte[] handleRequest(final PushedJobRequest request) {
    RECEIVED_JOBS.inc();

    if (observers.isEmpty()) {
      DROPPED_JOBS.inc();
      Loggers.JOB_STREAM.error(
          "No registered observer/client; job [%s] will be lost".formatted(request));
    }

    final var sortedObservers = new ArrayList<>(observers);
    Collections.shuffle(sortedObservers);

    final var observer = sortedObservers.get(0);
    // there isn't really a good way from here to verify when the push was received on the client
    // side, for this we would need distributed tracing
    try (final var timer = PUSH_DURATION.startTimer()) {
      final var job = ResponseMapper.toActivatedJobResponse(request.key(), request.job());
      observer.onNext(job);
      PUSHED_JOBS.inc();
    } catch (final Exception e) {
      observer.onError(e);
      removeObserver(observer);
      PUSHED_JOB_ERRORS.inc();
      Loggers.JOB_STREAM.error("Error occurred while pushing job {} to client", request);
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
