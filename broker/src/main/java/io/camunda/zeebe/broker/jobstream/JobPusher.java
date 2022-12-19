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
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import io.atomix.cluster.messaging.Subscription;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
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
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class JobPusher extends Actor implements ClusterMembershipEventListener {
  private static final Logger LOGGER = Loggers.JOB_STREAM;
  private static final Counter PUSHED_JOBS =
      Counter.build()
          .namespace("zeebe_job_stream")
          .name("broker_pushed_job")
          .help("Total count of pushed jobs")
          .register();
  private static final Counter PUSHED_JOB_ERRORS =
      Counter.build()
          .namespace("zeebe_job_stream")
          .name("broker_pushed_job_errors")
          .help("Total count of errors occurring when pushing a job")
          .register();
  private static final Gauge REGISTERED_GATEWAYS =
      Gauge.build()
          .namespace("zeebe_job_stream")
          .name("registered_gateway")
          .help("Total count of registered gateway endpoints")
          .register();
  private static final Histogram PUSH_LATENCY =
      Histogram.build()
          .namespace("zeebe_job_stream")
          .name("broker_push_latency")
          .help("Approximate duration of broker-to-gateway push")
          .register();

  private final ClusterEventService eventService;
  private final ClusterMembershipService membershipService;
  private final ManagedMessagingService messagingService;
  private final List<MemberId> gateways = new ArrayList<>();
  private final Collection<Subscription> subscriptions = new ArrayList<>();

  private int lastTargetGateway = 0;

  public JobPusher(final ClusterServices clusterServices) {
    // create own communication service; to make things easier, we'll use the same membership and
    // event services though

    eventService = clusterServices.getEventService();
    membershipService = clusterServices.getMembershipService();

    final var address = Address.from(clusterServices.getMessagingService().address().host(), 26503);
    final var messagingConfig =
        new MessagingConfig()
            .setPort(26503)
            .setCompressionAlgorithm(CompressionAlgorithm.SNAPPY)
            .setShutdownQuietPeriod(Duration.ZERO)
            .setShutdownTimeout(Duration.ofSeconds(1));
    messagingService = new NettyMessagingService("zeebe-cluster", address, messagingConfig);
  }

  @Override
  protected void onActorStarted() {
    REGISTERED_GATEWAYS.set(0);

    messagingService.start().join();

    subscriptions.add(
        eventService
            .subscribe("job-stream-register", (Consumer<String>) this::addGateway, actor::run)
            .join());
    subscriptions.add(
        eventService
            .subscribe("job-stream-unregister", (Consumer<String>) this::removeGateway, actor::run)
            .join());
    membershipService.addListener(this);
  }

  @Override
  protected void onActorClosing() {
    subscriptions.forEach(s -> CloseHelper.quietClose(s::close));
    membershipService.removeListener(this);
    messagingService.stop().join();
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
    if (gateways.contains(memberId)) {
      return;
    }

    gateways.add(memberId);
    LOGGER.debug("Registered new gateway {} for pushing", memberId);
    REGISTERED_GATEWAYS.inc();
  }

  public void push(final long key, final JobRecord job) {
    final var request = new PushedJobRequest().key(key).job(job);
    actor.run(() -> sendJob(request));
  }

  private void sendJob(final PushedJobRequest request) {
    // pseudo round-robin
    final var index = lastTargetGateway % gateways.size();
    final var targetGateway = gateways.get(index);
    lastTargetGateway = index + 1;

    final var timer = PUSH_LATENCY.startTimer();
    final var targetMember = membershipService.getMember(targetGateway);
    final var serializedRequest = serializeRequest(request);
    final var response =
        messagingService.sendAndReceive(
            Address.from(targetMember.address().host(), 26504),
            "job-stream-push",
            serializedRequest,
            true,
            Duration.ofSeconds(15));
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
