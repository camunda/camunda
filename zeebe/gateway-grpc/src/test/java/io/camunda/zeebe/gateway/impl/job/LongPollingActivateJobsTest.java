/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerErrorResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.job.FailJobStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestHandler;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.util.Either;
import io.grpc.Status.Code;
import io.grpc.StatusException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.util.unit.DataSize;

public final class LongPollingActivateJobsTest {

  private static final String TYPE = "test";

  private static final long LONG_POLLING_TIMEOUT = 5000;
  private static final long PROBE_TIMEOUT = 20000;
  private static final int FAILED_RESPONSE_THRESHOLD = 3;
  private static final int MAX_JOBS_TO_ACTIVATE = 2;
  private static final long MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4).toBytes();
  private final ControlledActorClock actorClock = new ControlledActorClock();
  @Rule public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(actorClock);
  private LongPollingActivateJobsHandler<ActivateJobsResponse> handler;
  private ActivateJobsStub activateJobsStub;
  private FailJobStub failJobStub;
  private int partitionsCount;
  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();
  private final AtomicLong requestIdGenerator = new AtomicLong(1);

  @Before
  public void setup() {
    handler =
        LongPollingActivateJobsHandler.<ActivateJobsResponse>newBuilder()
            .setBrokerClient(brokerClient)
            .setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setLongPollingTimeout(LONG_POLLING_TIMEOUT)
            .setProbeTimeoutMillis(PROBE_TIMEOUT)
            .setMinEmptyResponses(FAILED_RESPONSE_THRESHOLD)
            .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
            .setNoJobsReceivedExceptionProvider(Gateway.NO_JOBS_RECEIVED_EXCEPTION_PROVIDER)
            .setRequestCanceledExceptionProvider(Gateway.REQUEST_CANCELED_EXCEPTION_PROVIDER)
            .setMetrics(LongPollingMetrics.NOOP)
            .build();
    submitActorToActivateJobs(handler);

    activateJobsStub = spy(new ActivateJobsStub());
    activateJobsStub.registerWith(brokerClient);
    activateJobsStub.addAvailableJobs(TYPE, 0);

    failJobStub = spy(new FailJobStub());
    failJobStub.registerWith(brokerClient);

    partitionsCount = brokerClient.getTopologyManager().getTopology().getPartitionsCount();
  }

  @Test
  public void shouldBlockRequestsWhenResponseHasNoJobs() {
    // given
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        getLongPollingActivateJobsRequest();

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::hasScheduledTimer);
    verify(request.getResponseObserver(), times(0)).onCompleted();
  }

  @Test
  public void shouldUnblockRequestWhenJobsAvailable() {
    // given
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        getLongPollingActivateJobsRequest();

    handler.internalActivateJobsRetry(request);

    // when
    waitUntil(request::hasScheduledTimer);
    activateJobsStub.addAvailableJobs(TYPE, 1);
    verify(request.getResponseObserver(), times(0)).onCompleted();
    brokerClient.notifyJobsAvailable(TYPE);
    Awaitility.await().until(request::isCompleted);

    // then
    verify(request.getResponseObserver(), times(1)).onNext(any());
    verify(request.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  public void shouldBlockOnlyAfterForwardingUntilThreshold() throws Exception {
    // when
    final int amount = FAILED_RESPONSE_THRESHOLD;
    activateJobsAndWaitUntilBlocked(amount);

    // then
    verify(activateJobsStub, times(amount * partitionsCount)).handle(any());
  }

  @Test
  public void shouldBlockImmediatelyAfterThreshold() throws Exception {
    // given
    final int amount = FAILED_RESPONSE_THRESHOLD;
    activateJobsAndWaitUntilBlocked(amount);

    // when
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        getLongPollingActivateJobsRequest();
    handler.internalActivateJobsRetry(request);
    waitUntil(request::hasScheduledTimer);

    // then
    verify(activateJobsStub, times(amount * partitionsCount)).handle(any());
  }

  @Test
  public void shouldUnblockAllRequestsWhenJobsAvailable() throws Exception {
    // given
    final int amount = FAILED_RESPONSE_THRESHOLD;
    activateJobsAndWaitUntilBlocked(amount);
    final int firstRound = amount * partitionsCount;

    verify(activateJobsStub, times(firstRound)).handle(any());

    // when
    activateJobsStub.addAvailableJobs(TYPE, 1);
    brokerClient.notifyJobsAvailable(TYPE);

    // then

    // the job available notification triggers all three requests again
    final int invTriggeredByNotification = amount * partitionsCount;
    // the one request which has a result, re-triggers the remaining requests
    final int invTriggeredBySuccessfulRequest = (amount - 1) * partitionsCount;
    verify(
            activateJobsStub,
            timeout(2000)
                .times(firstRound + invTriggeredByNotification + invTriggeredBySuccessfulRequest))
        .handle(any());
  }

  @Test
  public void shouldCompleteAfterRequestTimeout() {
    // given
    final InflightActivateJobsRequest<ActivateJobsResponse> longPollingRequest =
        getLongPollingActivateJobsRequest();

    // when
    handler.internalActivateJobsRetry(longPollingRequest);
    waitUntil(longPollingRequest::hasScheduledTimer);
    actorClock.addTime(Duration.ofMillis(LONG_POLLING_TIMEOUT));
    waitUntil(longPollingRequest::isTimedOut);

    // then
    verify(longPollingRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  public void shouldCompleteFollowingRequestsAfterTimeout() {
    // given
    final List<InflightActivateJobsRequest<ActivateJobsResponse>> requests =
        activateJobsAndWaitUntilBlocked(FAILED_RESPONSE_THRESHOLD);
    actorClock.addTime(Duration.ofMillis(LONG_POLLING_TIMEOUT));
    requests.forEach(request -> waitUntil(request::isTimedOut));

    // when
    final InflightActivateJobsRequest<ActivateJobsResponse> successRequest =
        getLongPollingActivateJobsRequest();
    activateJobsStub.addAvailableJobs(TYPE, 1);
    brokerClient.notifyJobsAvailable(TYPE);
    handler.internalActivateJobsRetry(successRequest);
    Awaitility.await().until(successRequest::isCompleted);

    // then
    verify(successRequest.getResponseObserver(), times(1)).onNext(any());
    verify(successRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  public void shouldNotBlockOtherJobTypes() {
    // given
    final String otherType = "other-type";
    activateJobsStub.addAvailableJobs(otherType, 2);
    activateJobsAndWaitUntilBlocked(FAILED_RESPONSE_THRESHOLD);

    // when
    final InflightActivateJobsRequest<ActivateJobsResponse> otherRequest =
        getLongPollingActivateJobsRequest(otherType);
    handler.internalActivateJobsRetry(otherRequest);
    Awaitility.await().until(otherRequest::isCompleted);

    // then
    verify(otherRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  public void shouldProbeIfNoNotificationReceived() throws Exception {
    // given
    final long probeTimeout = 2000;
    handler =
        LongPollingActivateJobsHandler.<ActivateJobsResponse>newBuilder()
            .setBrokerClient(brokerClient)
            .setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setLongPollingTimeout(20000)
            .setProbeTimeoutMillis(probeTimeout)
            .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
            .setNoJobsReceivedExceptionProvider(Gateway.NO_JOBS_RECEIVED_EXCEPTION_PROVIDER)
            .setRequestCanceledExceptionProvider(Gateway.REQUEST_CANCELED_EXCEPTION_PROVIDER)
            .setMetrics(LongPollingMetrics.NOOP)
            .build();
    submitActorToActivateJobs(handler);

    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        getLongPollingActivateJobsRequest();
    handler.internalActivateJobsRetry(request);
    waitUntil(request::hasScheduledTimer);

    // when
    actorClock.addTime(Duration.ofMillis(probeTimeout));

    // then
    verify(activateJobsStub, timeout(2000).times(2 * partitionsCount)).handle(any());
  }

  @Test
  public void shouldProbeNextRequestWhenBlockedRequestsTimedOut() throws Exception {
    // given
    final long longPollingTimeout = 2000;
    final long probeTimeout = 20000;
    handler =
        LongPollingActivateJobsHandler.<ActivateJobsResponse>newBuilder()
            .setBrokerClient(brokerClient)
            .setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setLongPollingTimeout(longPollingTimeout)
            .setProbeTimeoutMillis(probeTimeout)
            .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
            .setNoJobsReceivedExceptionProvider(Gateway.NO_JOBS_RECEIVED_EXCEPTION_PROVIDER)
            .setRequestCanceledExceptionProvider(Gateway.REQUEST_CANCELED_EXCEPTION_PROVIDER)
            .setMetrics(LongPollingMetrics.NOOP)
            .build();
    submitActorToActivateJobs(handler);

    final int threshold = FAILED_RESPONSE_THRESHOLD;
    final List<InflightActivateJobsRequest<ActivateJobsResponse>> requests =
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
    verify(activateJobsStub, timeout(1000).times(totalRequests * partitionsCount)).handle(any());
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
    final InflightActivateJobsRequest<ActivateJobsResponse> longPollingRequest =
        toInflightActivateJobsRequest(request);

    handler.internalActivateJobsRetry(longPollingRequest);
    waitUntil(longPollingRequest::hasScheduledTimer);
    actorClock.addTime(Duration.ofMillis(requestTimeout));
    waitUntil(longPollingRequest::isTimedOut);

    // then
    verify(longPollingRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  public void shouldUseLargeRequestTimeout() {
    // given
    final long requestTimeout = 50000;
    final InflightActivateJobsRequest<ActivateJobsResponse> shortRequest =
        toInflightActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(1)
                .setRequestTimeout(requestTimeout)
                .build());

    final long longTimeout = 100000;
    final InflightActivateJobsRequest<ActivateJobsResponse> longRequest =
        toInflightActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(1)
                .setRequestTimeout(longTimeout)
                .build());

    handler.internalActivateJobsRetry(shortRequest);
    handler.internalActivateJobsRetry(longRequest);
    waitUntil(shortRequest::hasScheduledTimer);
    waitUntil(longRequest::hasScheduledTimer);

    // when
    actorClock.addTime(Duration.ofMillis(requestTimeout));
    waitUntil(shortRequest::isTimedOut);
    activateJobsStub.addAvailableJobs(TYPE, 2);
    brokerClient.notifyJobsAvailable(TYPE);
    Awaitility.await().until(longRequest::isCompleted);

    // then
    assertThat(longRequest.isTimedOut()).isFalse();
    verify(longRequest.getResponseObserver(), times(1)).onNext(any());
    verify(longRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  public void shouldNotBlockWhenNegativeTimeout() {
    // given
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        toInflightActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(1)
                .setRequestTimeout(-1)
                .build());

    // when
    handler.internalActivateJobsRetry(request);
    Awaitility.await().until(request::isCompleted);

    // then
    verify(request.getResponseObserver(), times(1)).onCompleted();
    assertThat(request.hasScheduledTimer()).isFalse();
    assertThat(request.isTimedOut()).isFalse();
  }

  @Test
  public void
      shouldRepeatActivateJobsRequestAgainstBrokersIfNewJobsArriveWhileIteratingThroughBrokersTheFirstTime() {
    // given

    // a request with timeout
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        toInflightActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(15)
                .setRequestTimeout(500)
                .build());

    /* and a request handler that simulates the following:
        - on the first round no broker has any jobs
        - about midway through iterating the brokers one of the brokers that has already been visited reports new jobs being available
        - these jobs are available, when the brokers are asked a second time
    */
    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        new RequestHandler<BrokerActivateJobsRequest, BrokerResponse<JobBatchRecord>>() {
          private final ActivateJobsStub noJobsAvailableStub = new ActivateJobsStub();
          private final ActivateJobsStub jobsAvailableStub = new ActivateJobsStub();
          private final Map<Integer, Integer> requestsPerPartitionCount = new HashMap<>();

          {
            jobsAvailableStub.addAvailableJobs(TYPE, 10);
          }

          @Override
          public BrokerResponse<JobBatchRecord> handle(final BrokerActivateJobsRequest request)
              throws Exception {
            final int partitionId = request.getPartitionId();

            final int requestsPerPartition =
                requestsPerPartitionCount.computeIfAbsent(partitionId, key -> 0);

            if (requestsPerPartition == 0) {
              requestsPerPartitionCount.put(partitionId, requestsPerPartition + 1);

              if (partitionId == 3) {
                brokerClient.notifyJobsAvailable(TYPE);
              }
              return noJobsAvailableStub.handle(request);
            } else {
              return jobsAvailableStub.handle(request);
            }
          }
        });
    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::isCompleted);

    // then
    assertThat(request.isTimedOut()).isFalse();
    final ArgumentCaptor<ActivateJobsResponse> responseArgumentCaptor =
        ArgumentCaptor.forClass(ActivateJobsResponse.class);
    verify(request.getResponseObserver()).onNext(responseArgumentCaptor.capture());

    final ActivateJobsResponse response = responseArgumentCaptor.getValue();

    assertThat(response.getJobsList()).hasSize(10);
  }

  @Test
  public void
      shouldReturnResourceExhaustedErrorIfNoJobsAvailableAndSomeBrokersReturnResourceExhaustionResponse() {
    // given
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        toInflightActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(15)
                .setRequestTimeout(500)
                .build());

    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        new RequestHandler<BrokerActivateJobsRequest, BrokerResponse<?>>() {
          private final ActivateJobsStub noJobsAvailableStub = new ActivateJobsStub();

          @Override
          public BrokerResponse<?> handle(final BrokerActivateJobsRequest request)
              throws Exception {
            final int partitionId = request.getPartitionId();

            if (partitionId == 4) {
              return new BrokerErrorResponse<>(
                  new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure"));
            } else {
              return noJobsAvailableStub.handle(request);
            }
          }
        });
    // when
    handler.internalActivateJobsRetry(request);
    Awaitility.await().until(request::isAborted);

    // then
    final ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(request.getResponseObserver(), times(1)).onError(throwableCaptor.capture());
    verify(request.getResponseObserver(), never()).onNext(Mockito.any());
    verify(request.getResponseObserver(), never()).onCompleted();

    assertThat(throwableCaptor.getValue()).isInstanceOf(StatusException.class);
    final StatusException exception = (StatusException) throwableCaptor.getValue();
    assertThat(exception.getStatus().getCode()).isEqualTo(Code.RESOURCE_EXHAUSTED);
  }

  @Test
  public void shouldReturnJobsIfSomeBrokersHaveJobsWhileOthersReturnResourceExhaustionResponse() {
    // given
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        toInflightActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(15)
                .setRequestTimeout(500)
                .build());

    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        new RequestHandler<BrokerActivateJobsRequest, BrokerResponse<?>>() {
          private final ActivateJobsStub jobsAvailableStub = new ActivateJobsStub();

          {
            jobsAvailableStub.addAvailableJobs(TYPE, 10);
          }

          @Override
          public BrokerResponse<?> handle(final BrokerActivateJobsRequest request)
              throws Exception {
            final int partitionId = request.getPartitionId();

            if (partitionId == 4) {
              return new BrokerErrorResponse<>(
                  new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure"));
            } else {
              return jobsAvailableStub.handle(request);
            }
          }
        });
    // when
    handler.internalActivateJobsRetry(request);

    waitUntil(request::isCompleted);

    // then
    assertThat(request.isTimedOut()).isFalse();
    final ArgumentCaptor<ActivateJobsResponse> responseArgumentCaptor =
        ArgumentCaptor.forClass(ActivateJobsResponse.class);
    verify(request.getResponseObserver()).onNext(responseArgumentCaptor.capture());

    final ActivateJobsResponse response = responseArgumentCaptor.getValue();

    assertThat(response.getJobsList()).hasSize(10);
  }

  @Test
  public void shouldRepeatRequestOnlyOnce() throws Exception {
    // given
    // the first three requests activates jobs
    final var firstRequest = getLongPollingActivateJobsRequest();
    final var secondRequest = getLongPollingActivateJobsRequest();
    final var thirdRequest = getLongPollingActivateJobsRequest();
    // the last request does not activate any jobs
    final var fourthRequest = getLongPollingActivateJobsRequest();

    final var allRequestsSubmittedLatch = new CountDownLatch(1);
    registerCustomHandlerWithNotification(
        (r) -> {
          try {
            // ensure that all requests are submitted to
            // the actor jobs queue before executing those
            allRequestsSubmittedLatch.await();
          } catch (final InterruptedException e) {
            // ignore
          }
        });

    activateJobsStub.addAvailableJobs(TYPE, 3 * MAX_JOBS_TO_ACTIVATE);

    // when
    handler.internalActivateJobsRetry(firstRequest);
    handler.internalActivateJobsRetry(secondRequest);
    handler.internalActivateJobsRetry(thirdRequest);
    handler.internalActivateJobsRetry(fourthRequest);

    allRequestsSubmittedLatch.countDown();
    waitUntil(fourthRequest::hasScheduledTimer);
    actorClock.addTime(Duration.ofMillis(LONG_POLLING_TIMEOUT));
    waitUntil(fourthRequest::isTimedOut);

    // then
    assertThat(firstRequest.isCompleted()).isTrue();
    assertThat(secondRequest.isCompleted()).isTrue();
    assertThat(thirdRequest.isCompleted()).isTrue();

    verify(activateJobsStub, times(1)).handle(firstRequest.getRequest());
    verify(activateJobsStub, times(1)).handle(secondRequest.getRequest());
    verify(activateJobsStub, times(1)).handle(thirdRequest.getRequest());
    verify(activateJobsStub, times(partitionsCount * 2)).handle(fourthRequest.getRequest());
  }

  @Test
  public void shouldCancelTimerOnResourceExhausted() {
    // given
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        getLongPollingActivateJobsRequest();

    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        new RequestHandler<BrokerActivateJobsRequest, BrokerResponse<?>>() {

          private int count = 0;

          /*
           * First execution of the request (count < partitionCount) -> don't activate jobs
           * Second execution of the request (count >= partitionCount) -> fail immediately
           */
          @Override
          public BrokerResponse<?> handle(final BrokerActivateJobsRequest request)
              throws Exception {
            if (count >= partitionsCount) {
              return new BrokerErrorResponse<>(
                  new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure"));
            }
            count += 1;
            return activateJobsStub.handle(request);
          }
        });
    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::hasScheduledTimer);
    brokerClient.notifyJobsAvailable(TYPE);
    Awaitility.await().until(request::isAborted);

    // then
    final ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(request.getResponseObserver(), times(1)).onError(throwableCaptor.capture());
    assertThat(throwableCaptor.getValue()).isInstanceOf(StatusException.class);

    assertThat(request.hasScheduledTimer()).isFalse();
  }

  @Test
  public void shouldCancelTimerOnBrokerRejectionException() {
    // given
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        getLongPollingActivateJobsRequest();

    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        new RequestHandler<BrokerActivateJobsRequest, BrokerResponse<?>>() {

          private int count = 0;

          /*
           * First execution of the request (count < partitionCount) -> don't activate jobs
           * Second execution of the request (count >= partitionCount) -> fail immediately
           */
          @Override
          public BrokerResponse<?> handle(final BrokerActivateJobsRequest request)
              throws Exception {
            if (count >= partitionsCount) {
              return new BrokerRejectionResponse<>(
                  new BrokerRejection(
                      Intent.UNKNOWN, 1, RejectionType.INVALID_ARGUMENT, "expected"));
            }
            count += 1;
            return activateJobsStub.handle(request);
          }
        });
    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::hasScheduledTimer);
    brokerClient.notifyJobsAvailable(TYPE);
    Awaitility.await().until(request::isAborted);

    // then
    final ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(request.getResponseObserver(), times(1)).onError(throwableCaptor.capture());
    assertThat(throwableCaptor.getValue()).isInstanceOf(BrokerRejectionException.class);

    assertThat(request.hasScheduledTimer()).isFalse();
  }

  @Test
  public void shouldCompleteRequestImmediatelyDespiteNotification() throws Exception {
    // given
    final InflightActivateJobsRequest<ActivateJobsResponse> request =
        toInflightActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setRequestTimeout(-1)
                .setMaxJobsToActivate(1)
                .build());

    registerCustomHandlerWithNotification(
        (r) -> {
          final var partitionId = r.getPartitionId();
          if (partitionId == 1) {
            brokerClient.notifyJobsAvailable(TYPE);
          }
        });

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::isCompleted);
    verify(activateJobsStub, times(partitionsCount)).handle(any());
  }

  @Test
  public void shouldTimeOutRequestDespiteMultipleNotificationLoops() throws Exception {
    // given
    final var request = getLongPollingActivateJobsRequest();

    registerCustomHandlerWithNotification(
        (r) -> {
          final var partitionId = r.getPartitionId();
          if (partitionId == 1) {
            brokerClient.notifyJobsAvailable(TYPE);
          }
        });

    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::hasScheduledTimer);
    actorClock.addTime(Duration.ofMillis(LONG_POLLING_TIMEOUT));
    waitUntil(request::isTimedOut);

    // then
    verify(activateJobsStub, atLeast(partitionsCount)).handle(any());
  }

  @Test
  public void shouldNotContinueWithNextPartitionIfResponseIsNotSend() throws Exception {
    // given
    final var request =
        spy(
            toInflightActivateJobsRequest(
                ActivateJobsRequest.newBuilder()
                    .setType(TYPE)
                    .setMaxJobsToActivate(3 * MAX_JOBS_TO_ACTIVATE)
                    .setRequestTimeout(500)
                    .build()));

    activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);

    final var responseNotSent = Either.right(false);
    doReturn(responseNotSent).when(request).tryToSendActivatedJobs(any());

    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::isAborted);

    // then
    verify(activateJobsStub, times(1)).handle(any());
  }

  @Test
  public void shouldNotContinueWithNextPartitionIfResponseFailed() throws Exception {
    // given
    final var request =
        toInflightActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(3 * MAX_JOBS_TO_ACTIVATE)
                .setRequestTimeout(500)
                .build());

    activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);

    final var sendResponseException = new RuntimeException("foo");
    doThrow(sendResponseException).when(request.getResponseObserver()).onNext(any());

    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::isAborted);

    // then
    verify(activateJobsStub, times(1)).handle(any());
  }

  @Test
  public void shouldMakeAllActivatedJobReactivatableWhenJobsAreNotSend() throws Exception {
    // given
    activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);
    final var request = spy(getLongPollingActivateJobsRequest());

    final var responseNotSent = Either.right(false);
    doReturn(responseNotSent).when(request).tryToSendActivatedJobs(any());

    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::isAborted);

    // then
    verify(request.getResponseObserver(), times(1)).onError(any());
    verify(failJobStub, times(MAX_JOBS_TO_ACTIVATE)).handle(any());
  }

  @Test
  public void shouldMakeAllActivatedJobReactivatableWhenJobsAreNotSendDueException()
      throws Exception {
    // given
    activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);
    final var request = getLongPollingActivateJobsRequest();

    final var sendResponseException = new RuntimeException("foo");
    doThrow(sendResponseException).when(request.getResponseObserver()).onNext(any());

    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::isAborted);

    // then
    verify(request.getResponseObserver(), times(1)).onError(any());
    verify(failJobStub, times(MAX_JOBS_TO_ACTIVATE)).handle(any());
  }

  @Test
  public void shouldOnlyMakeJobsReactivatableInCurrentIterationWhenJobsAreNotReturned()
      throws Exception {
    // given
    final var responseNotSent = Either.right(false);

    final var request =
        spy(
            toInflightActivateJobsRequest(
                ActivateJobsRequest.newBuilder()
                    .setType(TYPE)
                    .setMaxJobsToActivate(3 * MAX_JOBS_TO_ACTIVATE)
                    .setRequestTimeout(500)
                    .build()));

    registerCustomHandlerWithNotification(
        (r) -> {
          final var partitionId = r.getPartitionId();
          if (partitionId == 1) {
            activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);
          } else if (partitionId == 2) {
            activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);
            doReturn(responseNotSent).when(request).tryToSendActivatedJobs(any());
          }
        });

    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::isAborted);

    // then
    verify(request.getResponseObserver(), times(1)).onNext(any());
    verify(activateJobsStub, times(2)).handle(request.getRequest());

    verify(request.getResponseObserver(), times(1)).onError(any());
    verify(failJobStub, times(MAX_JOBS_TO_ACTIVATE)).handle(any());
  }

  @Test
  public void shouldOnlyMakeJobsReactivatableInCurrentIterationWhenJobsAreNotReturnedDueException()
      throws Exception {
    // given
    final var request =
        toInflightActivateJobsRequest(
            ActivateJobsRequest.newBuilder()
                .setType(TYPE)
                .setMaxJobsToActivate(3 * MAX_JOBS_TO_ACTIVATE)
                .setRequestTimeout(500)
                .build());

    final var responseObserver = request.getResponseObserver();
    final var sendResponseException = new RuntimeException("foo");

    registerCustomHandlerWithNotification(
        (r) -> {
          final var partitionId = r.getPartitionId();
          if (partitionId == 1) {
            activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);
          } else if (partitionId == 2) {
            activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);
            doThrow(sendResponseException).when(responseObserver).onNext(any());
          }
        });

    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::isAborted);

    // then
    verify(responseObserver, times(2)).onNext(any());
    verify(activateJobsStub, times(2)).handle(request.getRequest());

    verify(responseObserver, times(1)).onError(any());
    verify(failJobStub, times(MAX_JOBS_TO_ACTIVATE)).handle(any());
  }

  @Test
  public void shouldSetCurrentRetriesAndNoBackoff() {
    // given
    final var activatedJobRef = new AtomicReference<ActivatedJob>();
    activateJobsStub.addAvailableJobs(TYPE, 1);
    final var grpcRequest =
        ActivateJobsRequest.newBuilder()
            .setType(TYPE)
            .setMaxJobsToActivate(MAX_JOBS_TO_ACTIVATE)
            .setRequestTimeout(500)
            .build();
    final var request =
        new InflightActivateJobsRequest<ActivateJobsResponse>(
            getNextRequestId(),
            RequestMapper.toActivateJobsRequest(grpcRequest),
            spy(ServerStreamObserver.class),
            grpcRequest.getRequestTimeout()) {

          @Override
          public Either<Exception, Boolean> tryToSendActivatedJobs(
              final ActivateJobsResponse grpcResponse) {
            activatedJobRef.set(grpcResponse.getJobs(0));
            super.tryToSendActivatedJobs(grpcResponse);
            return Either.right(false);
          }
        };

    // when
    handler.internalActivateJobsRetry(request);
    waitUntil(request::isAborted);

    // then
    final var brokerRequests = brokerClient.getBrokerRequests();
    assertThat(brokerRequests)
        .describedAs("Expected 2 requests: 1 to activate jobs and 1 to fail a job")
        .hasSize(2);

    final var firstBrokerRequest = brokerRequests.get(0);
    assertThat(firstBrokerRequest).isInstanceOf(BrokerActivateJobsRequest.class);

    final var secondBrokerRequest = brokerRequests.get(1);
    assertThat(secondBrokerRequest).isInstanceOf(BrokerFailJobRequest.class);

    final var failRequest = (BrokerFailJobRequest) secondBrokerRequest;
    final var brokerRequestValue = failRequest.getRequestWriter();
    final var activatedJob = activatedJobRef.get();

    assertThat(failRequest.getKey()).isEqualTo(activatedJob.getKey());
    assertThat(brokerRequestValue.getRetries()).isEqualTo(activatedJob.getRetries());
    assertThat(brokerRequestValue.getRetryBackoff()).isEqualTo(0);
    assertThat(brokerRequestValue.getErrorMessageBuffer()).isNotNull();
  }

  private List<InflightActivateJobsRequest<ActivateJobsResponse>> activateJobsAndWaitUntilBlocked(
      final int amount) {
    return IntStream.range(0, amount)
        .boxed()
        .map(
            i -> {
              final InflightActivateJobsRequest<ActivateJobsResponse> request =
                  getLongPollingActivateJobsRequest();
              handler.internalActivateJobsRetry(request);
              waitUntil(request::hasScheduledTimer);
              return request;
            })
        .collect(Collectors.toList());
  }

  private InflightActivateJobsRequest<ActivateJobsResponse> getLongPollingActivateJobsRequest() {
    return getLongPollingActivateJobsRequest(TYPE);
  }

  private InflightActivateJobsRequest<ActivateJobsResponse> getLongPollingActivateJobsRequest(
      final String jobType) {
    return toInflightActivateJobsRequest(
        ActivateJobsRequest.newBuilder()
            .setType(jobType)
            .setMaxJobsToActivate(MAX_JOBS_TO_ACTIVATE)
            .build());
  }

  private InflightActivateJobsRequest<ActivateJobsResponse> toInflightActivateJobsRequest(
      final ActivateJobsRequest grpcRequest) {
    return new InflightActivateJobsRequest<ActivateJobsResponse>(
        getNextRequestId(),
        RequestMapper.toActivateJobsRequest(grpcRequest),
        spy(ServerStreamObserver.class),
        grpcRequest.getRequestTimeout());
  }

  private long getNextRequestId() {
    return requestIdGenerator.getAndIncrement();
  }

  private void registerCustomHandlerWithNotification(
      final Consumer<BrokerActivateJobsRequest> notification) {
    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        (final BrokerActivateJobsRequest request) -> {
          notification.accept(request);
          return activateJobsStub.handle(request);
        });
  }

  private void submitActorToActivateJobs(
      final LongPollingActivateJobsHandler<ActivateJobsResponse> handler) {
    final var future = new CompletableFuture<>();
    final var actor =
        Actor.newActor()
            .name("LongPollingHandler-Test")
            .actorStartedHandler(handler.andThen(future::complete))
            .build();
    actorSchedulerRule.submitActor(actor);
    future.join();
  }
}
