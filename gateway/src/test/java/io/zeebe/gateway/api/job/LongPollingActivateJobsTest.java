/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.job;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.api.util.StubbedBrokerClient;
import io.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.zeebe.gateway.impl.job.LongPollingActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LongPollingActivateJobsTest {

  private static final String TYPE = "test";

  private static final long LONG_POLLING_TIMEOUT = 5000;
  private static final long PROBE_TIMEOUT = 20000;
  private static final int FAILED_RESPONSE_THRESHOLD = 3;
  protected ControlledActorClock actorClock = new ControlledActorClock();
  @Rule public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(actorClock);
  private LongPollingActivateJobsHandler handler;
  private ActivateJobsStub stub;
  private int partitionsCount;
  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();

  @Before
  public void setup() {
    handler =
        LongPollingActivateJobsHandler.newBuilder()
            .setBrokerClient(brokerClient)
            .setLongPollingTimeout(LONG_POLLING_TIMEOUT)
            .setProbeTimeoutMillis(PROBE_TIMEOUT)
            .setMinEmptyResponses(FAILED_RESPONSE_THRESHOLD)
            .build();
    actorSchedulerRule.submitActor(handler);
    stub = spy(new ActivateJobsStub());
    stub.registerWith(brokerClient);
    stub.addAvailableJobs(TYPE, 0);
    partitionsCount = brokerClient.getTopologyManager().getTopology().getPartitionsCount();
  }

  @Test
  public void shouldBlockRequestsWhenResponseHasNoJobs() {
    // given
    final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();

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

    handler.activateJobs(request);

    // when
    waitUntil(() -> request.hasScheduledTimer());
    stub.addAvailableJobs(TYPE, 1);
    verify(responseSpy, times(0)).onCompleted();
    brokerClient.notifyJobsAvailable(TYPE);

    // then
    verify(responseSpy, timeout(2000).times(1)).onNext(any());
    verify(responseSpy, timeout(1000).times(1)).onCompleted();
  }

  @Test
  public void shouldBlockOnlyAfterForwardingUntilThreshold() throws Exception {
    // when
    final int amount = FAILED_RESPONSE_THRESHOLD;
    activateJobsAndWaitUntilBlocked(amount);

    // then
    verify(stub, times(amount * partitionsCount)).handle(any());
  }

  @Test
  public void shouldBlockImmediatelyAfterThreshold() throws Exception {
    // given
    final int amount = FAILED_RESPONSE_THRESHOLD;
    activateJobsAndWaitUntilBlocked(amount);

    // when
    final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();
    handler.activateJobs(request);
    waitUntil(() -> request.hasScheduledTimer());

    // then
    verify(stub, times(amount * partitionsCount)).handle(any());
  }

  @Test
  public void shouldUnblockAllRequestsWhenJobsAvailable() throws Exception {
    // given
    final int amount = 3;
    activateJobsAndWaitUntilBlocked(amount);

    verify(stub, times(amount * partitionsCount)).handle(any());

    // when
    stub.addAvailableJobs(TYPE, 1);
    brokerClient.notifyJobsAvailable(TYPE);

    // then
    verify(stub, timeout(2000).times(2 * amount * partitionsCount)).handle(any());
  }

  @Test
  public void shouldCompleteAfterRequestTimeout() {
    // given
    final LongPollingActivateJobsRequest longPollingRequest = getLongPollingActivateJobsRequest();

    // when
    handler.activateJobs(longPollingRequest);
    waitUntil(() -> longPollingRequest.hasScheduledTimer());
    actorClock.addTime(Duration.ofMillis(LONG_POLLING_TIMEOUT));
    waitUntil(() -> longPollingRequest.isTimedOut());

    // then
    verify(longPollingRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  public void shouldCompleteFollowingRequestsAfterTimeout() {
    // given
    final List<LongPollingActivateJobsRequest> requests =
        activateJobsAndWaitUntilBlocked(FAILED_RESPONSE_THRESHOLD);
    actorClock.addTime(Duration.ofMillis(LONG_POLLING_TIMEOUT));
    requests.forEach(request -> waitUntil(() -> request.isTimedOut()));

    // when
    final LongPollingActivateJobsRequest successRequest = getLongPollingActivateJobsRequest();
    stub.addAvailableJobs(TYPE, 1);
    brokerClient.notifyJobsAvailable(TYPE);
    handler.activateJobs(successRequest);

    // then
    verify(successRequest.getResponseObserver(), timeout(2000).times(1)).onNext(any());
    verify(successRequest.getResponseObserver(), timeout(1000).times(1)).onCompleted();
  }

  @Test
  public void shouldNotBlockOtherJobTypes() {
    // given
    final String otherType = "other-type";
    stub.addAvailableJobs(otherType, 2);
    final int threshold = 3;
    activateJobsAndWaitUntilBlocked(threshold);

    // when
    final LongPollingActivateJobsRequest otherRequest =
        getLongPollingActivateJobsRequest(otherType);
    handler.activateJobs(otherRequest);

    // then
    verify(otherRequest.getResponseObserver(), timeout(2000).times(1)).onCompleted();
  }

  @Test
  public void shouldProbeIfNoNotificationReceived() throws Exception {
    // given
    final long probeTimeout = 2000;
    handler =
        LongPollingActivateJobsHandler.newBuilder()
            .setBrokerClient(brokerClient)
            .setLongPollingTimeout(20000)
            .setProbeTimeoutMillis(probeTimeout)
            .build();
    actorSchedulerRule.submitActor(handler);

    final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();
    handler.activateJobs(request);
    waitUntil(() -> request.hasScheduledTimer());

    // when
    actorClock.addTime(Duration.ofMillis(probeTimeout));

    // then
    verify(stub, timeout(2000).times(2 * partitionsCount)).handle(any());
  }

  @Test
  public void shouldProbeNextRequestWhenBlockedRequestsTimedOut() throws Exception {
    // given
    final long longPollingTimeout = 2000;
    final long probeTimeout = 20000;
    handler =
        LongPollingActivateJobsHandler.newBuilder()
            .setBrokerClient(brokerClient)
            .setLongPollingTimeout(longPollingTimeout)
            .setProbeTimeoutMillis(probeTimeout)
            .build();
    actorSchedulerRule.submitActor(handler);

    final int threshold = 3;
    final List<LongPollingActivateJobsRequest> requests =
        activateJobsAndWaitUntilBlocked(threshold);

    actorClock.addTime(Duration.ofMillis(longPollingTimeout));
    requests.forEach(
        request -> verify(request.getResponseObserver(), timeout(1000).times(1)).onCompleted());

    // when
    actorClock.addTime(Duration.ofMillis(probeTimeout));
    Thread.sleep(100); // Give some time for the periodic probe to execute
    activateJobsAndWaitUntilBlocked(1);

    // then
    final int totalRequests = threshold + 1;
    verify(stub, timeout(1000).times(totalRequests * partitionsCount)).handle(any());
  }

  @Test
  public void shouldUseRequestSpecificTimeout() {
    final int requestTimeout = 1000;
    final ActivateJobsRequest request =
        ActivateJobsRequest.newBuilder()
            .setType(TYPE)
            .setMaxJobsToActivate(1)
            .setRequestTimeout(requestTimeout)
            .build();
    final StreamObserver responseSpy = spy(StreamObserver.class);

    final LongPollingActivateJobsRequest longPollingRequest =
        new LongPollingActivateJobsRequest(request, responseSpy);

    handler.activateJobs(longPollingRequest);
    waitUntil(() -> longPollingRequest.hasScheduledTimer());
    actorClock.addTime(Duration.ofMillis(requestTimeout));
    waitUntil(() -> longPollingRequest.isTimedOut());

    // then
    verify(longPollingRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  public void shouldUseLargeRequestTimeout() {
    // given
    final long requestTimeout = 50000;
    final LongPollingActivateJobsRequest shortRequest =
        new LongPollingActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(1)
                .setRequestTimeout(requestTimeout)
                .build(),
            spy(StreamObserver.class));

    final long longTimeout = 100000;
    final LongPollingActivateJobsRequest longRequest =
        new LongPollingActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(1)
                .setRequestTimeout(longTimeout)
                .build(),
            spy(StreamObserver.class));

    handler.activateJobs(shortRequest);
    handler.activateJobs(longRequest);
    waitUntil(() -> shortRequest.hasScheduledTimer());
    waitUntil(() -> longRequest.hasScheduledTimer());

    // when
    actorClock.addTime(Duration.ofMillis(requestTimeout));
    waitUntil(() -> shortRequest.isTimedOut());
    stub.addAvailableJobs(TYPE, 2);
    brokerClient.notifyJobsAvailable(TYPE);

    // then
    assertThat(longRequest.isTimedOut()).isFalse();
    verify(longRequest.getResponseObserver(), timeout(1000).times(1)).onNext(any());
    verify(longRequest.getResponseObserver(), timeout(1000).times(1)).onCompleted();
  }

  @Test
  public void shouldNotBlockWhenNegativeTimeout() {
    // given
    final LongPollingActivateJobsRequest request =
        new LongPollingActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(1)
                .setRequestTimeout(-1)
                .build(),
            spy(StreamObserver.class));

    // when
    handler.activateJobs(request);
    verify(request.getResponseObserver(), timeout(1000).times(1)).onCompleted();

    // then
    assertThat(request.hasScheduledTimer()).isFalse();
    assertThat(request.isTimedOut()).isFalse();
  }

  private List<LongPollingActivateJobsRequest> activateJobsAndWaitUntilBlocked(int amount) {
    return IntStream.range(0, amount)
        .boxed()
        .map(
            i -> {
              final LongPollingActivateJobsRequest request = getLongPollingActivateJobsRequest();
              handler.activateJobs(request);
              waitUntil(() -> request.hasScheduledTimer());
              return request;
            })
        .collect(Collectors.toList());
  }

  private LongPollingActivateJobsRequest getLongPollingActivateJobsRequest() {
    return getLongPollingActivateJobsRequest(TYPE);
  }

  private LongPollingActivateJobsRequest getLongPollingActivateJobsRequest(String jobType) {
    final int maxJobsToActivate = 2;
    final ActivateJobsRequest request =
        ActivateJobsRequest.newBuilder()
            .setType(jobType)
            .setMaxJobsToActivate(maxJobsToActivate)
            .build();
    final StreamObserver responseSpy = spy(StreamObserver.class);

    return new LongPollingActivateJobsRequest(request, responseSpy);
  }
}
