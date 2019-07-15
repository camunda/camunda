/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.job;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.zeebe.gateway.impl.job.LongPollingActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;

public class LongPollingActivateJobsTest extends GatewayTest {

  private static final String TYPE = "test";
  private LongPollingActivateJobsHandler handler;
  private ActivateJobsStub stub;
  private int partitionsCount;

  @Before
  public void setup() {
    handler = new LongPollingActivateJobsHandler(brokerClient);
    actorSchedulerRule.submitActor(handler);
    stub = spy(new ActivateJobsStub());
    stub.registerWith(gateway);
    partitionsCount = brokerClient.getTopologyManager().getTopology().getPartitionsCount();
  }

  @Test
  public void shouldBlockRequestsWhenResponseHasNoJobs() {
    // given
    final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();
    stub.addAvailableJobs(TYPE, 0);

    // when
    handler.activateJobs(request);

    // then
    waitUntil(() -> request.hasScheduledTimer());
    verify(request.getResponseObserver(), times(0)).onCompleted();
  }

  @Test
  public void shouldUnblockRequestWhenJobsAvailable() {
    // given
    final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();
    final StreamObserver<ActivateJobsResponse> responseSpy = request.getResponseObserver();
    stub.addAvailableJobs(TYPE, 0);

    handler.activateJobs(request);

    // when
    waitUntil(() -> request.hasScheduledTimer());
    stub.addAvailableJobs(TYPE, 1);
    verify(responseSpy, times(0)).onCompleted();
    gateway.notifyJobsAvailable(TYPE);

    // then
    verify(responseSpy, timeout(1000).times(1)).onNext(any());
    verify(responseSpy, times(1)).onCompleted();
  }

  @Test
  public void shouldBlockOnlyAfterForwardingUntilThreshold() throws Exception {
    // when
    final int threshold = 3;
    IntStream.range(0, threshold)
        .forEach(
            i -> {
              final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();
              handler.activateJobs(request);
              waitUntil(() -> request.hasScheduledTimer());
            });

    // then
    verify(stub, times(threshold * partitionsCount)).handle(any());
  }

  @Test
  public void shouldBlockImmediatelyAfterThreshold() throws Exception {
    // given
    final int threshold = 3;
    IntStream.range(0, threshold)
        .forEach(
            i -> {
              final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();
              handler.activateJobs(request);
              waitUntil(() -> request.hasScheduledTimer());
            });

    // when
    final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();
    handler.activateJobs(request);
    waitUntil(() -> request.hasScheduledTimer());

    // then
    verify(stub, times(threshold * partitionsCount)).handle(any());
  }

  @Test
  public void shouldUnblockAllRequestsWhenJobsAvailable() throws Exception {
    // given
    final int numRequests = 3;
    IntStream.range(0, numRequests)
        .forEach(
            i -> {
              final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();
              handler.activateJobs(request);
              waitUntil(() -> request.hasScheduledTimer());
            });

    verify(stub, times(numRequests * partitionsCount)).handle(any());

    // when
    stub.addAvailableJobs(TYPE, 1);
    gateway.notifyJobsAvailable(TYPE);

    // then
    verify(stub, timeout(1000).times(2 * numRequests * partitionsCount)).handle(any());
  }

  @Test
  public void shouldUnblockAfterRequestTimeout() {
    // given

    final LongPollingActivateJobsRequest longPollingRequest = getLongPollingActivateJobsRequest();
    stub.addAvailableJobs(TYPE, 0);

    // when
    handler.activateJobs(longPollingRequest);
    waitUntil(() -> longPollingRequest.hasScheduledTimer());
    actorClock.addTime(LongPollingActivateJobsHandler.DEFAULT_LONG_POLLING_TIMEOUT);
    waitUntil(() -> longPollingRequest.isTimedOut(), 200);

    // then
    verify(longPollingRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  public void shouldCompleteFollowingRequestsAfterTimeout() {

    final LongPollingActivateJobsRequest timeoutRequest1 = getLongPollingActivateJobsRequest();
    handler.activateJobs(timeoutRequest1);
    final LongPollingActivateJobsRequest timeoutRequest2 = getLongPollingActivateJobsRequest();
    handler.activateJobs(timeoutRequest2);
    final LongPollingActivateJobsRequest timeoutRequest3 = getLongPollingActivateJobsRequest();
    handler.activateJobs(timeoutRequest3);
    waitUntil(() -> timeoutRequest3.hasScheduledTimer());
    actorClock.addTime(LongPollingActivateJobsHandler.DEFAULT_LONG_POLLING_TIMEOUT);
    waitUntil(() -> timeoutRequest1.isTimedOut(), 200);
    waitUntil(() -> timeoutRequest2.isTimedOut(), 200);
    waitUntil(() -> timeoutRequest3.isTimedOut(), 200);

    // when
    final LongPollingActivateJobsRequest successRequest = getLongPollingActivateJobsRequest();
    stub.addAvailableJobs(TYPE, 1);
    gateway.notifyJobsAvailable(TYPE);
    handler.activateJobs(successRequest);

    // then
    verify(successRequest.getResponseObserver(), timeout(1000).times(1)).onNext(any());
    verify(successRequest.getResponseObserver(), times(1)).onCompleted();
  }

  private LongPollingActivateJobsRequest getLongPollingActivateJobsRequest() {
    final int maxJobsToActivate = 2;
    final ActivateJobsRequest request =
        ActivateJobsRequest.newBuilder()
            .setType(TYPE)
            .setMaxJobsToActivate(maxJobsToActivate)
            .build();
    final StreamObserver responseSpy = spy(StreamObserver.class);

    return new LongPollingActivateJobsRequest(request, responseSpy);
  }
}
