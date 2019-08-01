/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.job;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ScheduledTimer;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import org.slf4j.Logger;

public class LongPollingActivateJobsHandler extends Actor {

  public static final Duration DEFAULT_LONG_POLLING_TIMEOUT = Duration.ofSeconds(10);
  private static final String JOBS_AVAILABLE_TOPIC = "jobsAvailable";
  private static final int NO_JOBS_RESPONSE_THRESHOLD = 3;
  private static final Logger LOG = Loggers.GATEWAY_LOGGER;

  private final ActivateJobsHandler activateJobsHandler;
  private final BrokerClient brokerClient;
  private final Map<String, Integer> zeroResponses = new HashMap<>();
  private final Map<String, Queue<LongPollingActivateJobsRequest>> blockedRequests =
      new HashMap<>();

  public LongPollingActivateJobsHandler(BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
    this.activateJobsHandler = new ActivateJobsHandler(brokerClient);
  }

  public void activateJobs(
      ActivateJobsRequest request, StreamObserver<ActivateJobsResponse> responseObserver) {
    final LongPollingActivateJobsRequest longPollingRequest =
        new LongPollingActivateJobsRequest(request, responseObserver);
    activateJobs(longPollingRequest);
  }

  public void activateJobs(LongPollingActivateJobsRequest request) {
    actor.run(
        () -> {
          if (isJobAvailable(request.getType())) {
            final BrokerClusterState topology = brokerClient.getTopologyManager().getTopology();
            if (topology != null) {
              final int partitionsCount = topology.getPartitionsCount();
              activateJobsHandler.activateJobs(
                  partitionsCount,
                  request.getRequest(),
                  request.getMaxJobsToActivate(),
                  request.getType(),
                  response -> onResponse(request, response),
                  remainingAmount -> onCompleted(request, remainingAmount));
            }
          } else {
            block(request);
          }
        });
  }

  @Override
  protected void onActorStarting() {
    brokerClient.subscribeJobAvailableNotification(JOBS_AVAILABLE_TOPIC, this::onNotification);
  }

  private void onNotification(String jobType) {
    LOG.trace("Received jobs available notification for type {}.", jobType);
    actor.call(() -> jobsAvailable(jobType));
  }

  private void onCompleted(LongPollingActivateJobsRequest request, Integer remainingAmount) {
    if (remainingAmount == request.getMaxJobsToActivate()) {
      actor.submit(() -> jobsNotAvailable(request));
    } else {
      actor.submit(() -> request.complete());
    }
  }

  private void onResponse(
      LongPollingActivateJobsRequest request, ActivateJobsResponse activateJobsResponse) {
    actor.submit(
        () -> {
          request.onResponse(activateJobsResponse);
          jobsAvailable(request.getType());
        });
  }

  private void jobsNotAvailable(LongPollingActivateJobsRequest request) {
    zeroResponses.compute(request.getType(), (t, count) -> count == null ? 1 : count + 1);
    block(request);
  }

  private void jobsAvailable(String jobType) {
    zeroResponses.remove(jobType);
    unblock(jobType);
  }

  private void unblock(String jobType) {
    final Queue<LongPollingActivateJobsRequest> requests = blockedRequests.remove(jobType);
    if (requests == null) {
      return;
    }
    requests.forEach(
        request -> {
          LOG.trace("Unblocking ActivateJobsRequest {}", request.getRequest());
          activateJobs(request);
        });
  }

  private boolean isJobAvailable(String jobType) {
    final Integer notAvailableResponses = zeroResponses.get(jobType);
    return notAvailableResponses == null || notAvailableResponses < NO_JOBS_RESPONSE_THRESHOLD;
  }

  private void block(LongPollingActivateJobsRequest request) {
    if (!request.isTimedOut()) {
      LOG.trace(
          "Jobs of type {} not available. Blocking request {}",
          request.getType(),
          request.getRequest());
      blockedRequests.computeIfAbsent(request.getType(), t -> new LinkedList<>()).offer(request);
      if (!request.hasScheduledTimer()) {
        addTimeOut(request);
      }
    }
  }

  private void addTimeOut(LongPollingActivateJobsRequest request) {
    final ScheduledTimer timeout =
        actor.runDelayed(
            DEFAULT_LONG_POLLING_TIMEOUT,
            () -> {
              try {
                final Queue<LongPollingActivateJobsRequest> requests =
                    blockedRequests.get(request.getType());
                if (requests != null) {
                  requests.remove(request);
                }
                request.timeout();
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
    request.setScheduledTimer(timeout);
  }
}
