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
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.authentication.tenant.TenantAttributeHolder;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerErrorResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.job.FailJobStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestHandler;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import io.camunda.zeebe.gateway.protocol.rest.ActivatedJobResult;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.JobActivationRequestResponseObserver;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

public class LongPollingActivateJobsRestTest {

  static final String TYPE = "test";

  static final long LONG_POLLING_TIMEOUT = 5000;
  static final long PROBE_TIMEOUT = 20000;
  static final int FAILED_RESPONSE_THRESHOLD = 3;
  static final int MAX_JOBS_TO_ACTIVATE = 2;
  static final long MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4).toBytes();

  final ControlledActorClock actorClock = new ControlledActorClock();
  final StubbedBrokerClient brokerClient = new StubbedBrokerClient();
  final int partitionsCount = brokerClient.getTopologyManager().getTopology().getPartitionsCount();
  final AtomicLong requestIdGenerator = new AtomicLong(1);

  ActorScheduler actorScheduler;
  LongPollingActivateJobsHandler<JobActivationResult> handler;
  ActivateJobsStub activateJobsStub;
  FailJobStub failJobStub;
  MockedStatic<TenantAttributeHolder> tenantAttributeHolderMock;

  @BeforeEach
  void setUp() {
    actorScheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(
                Math.max(1, Runtime.getRuntime().availableProcessors() - 2))
            .setIoBoundActorThreadCount(2)
            .setActorClock(actorClock)
            .build();
    actorScheduler.start();

    handler =
        LongPollingActivateJobsHandler.<JobActivationResult>newBuilder()
            .setBrokerClient(brokerClient)
            .setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setLongPollingTimeout(LONG_POLLING_TIMEOUT)
            .setProbeTimeoutMillis(PROBE_TIMEOUT)
            .setMinEmptyResponses(FAILED_RESPONSE_THRESHOLD)
            .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
            .setNoJobsReceivedExceptionProvider(
                msg -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, msg))
            .setRequestCanceledExceptionProvider(
                msg -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg))
            .setMetrics(LongPollingMetrics.noop())
            .build();
    submitActorToActivateJobs(handler);

    activateJobsStub = spy(new ActivateJobsStub());
    activateJobsStub.registerWith(brokerClient);
    failJobStub = spy(new FailJobStub());
    failJobStub.registerWith(brokerClient);

    tenantAttributeHolderMock = mockStatic(TenantAttributeHolder.class);
    tenantAttributeHolderMock.when(TenantAttributeHolder::getTenantIds).thenReturn(Set.of());
  }

  @AfterEach
  void tearDown() throws Exception {
    tenantAttributeHolderMock.close();
    actorScheduler.close();
    actorScheduler = null;
  }

  @Test
  void shouldBlockRequestsWhenResponseHasNoJobs() {
    // given
    final InflightActivateJobsRequest<JobActivationResult> request =
        getLongPollingJobActivationRequest();

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::hasScheduledTimer);
    verify(request.getResponseObserver(), times(0)).onCompleted();
  }

  @Test
  void shouldUnblockRequestWhenJobsAvailable() {
    // given
    final InflightActivateJobsRequest<JobActivationResult> request =
        getLongPollingJobActivationRequest();

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
  void shouldBlockOnlyAfterForwardingUntilThreshold() throws Exception {
    // when
    final int amount = FAILED_RESPONSE_THRESHOLD;
    activateJobsAndWaitUntilBlocked(amount);

    // then
    verify(activateJobsStub, times(amount * partitionsCount)).handle(any());
  }

  @Test
  void shouldBlockImmediatelyAfterThreshold() throws Exception {
    // given
    final int amount = FAILED_RESPONSE_THRESHOLD;
    activateJobsAndWaitUntilBlocked(amount);

    // when
    final InflightActivateJobsRequest<JobActivationResult> request =
        getLongPollingJobActivationRequest();
    handler.internalActivateJobsRetry(request);
    waitUntil(request::hasScheduledTimer);

    // then
    verify(activateJobsStub, times(amount * partitionsCount)).handle(any());
  }

  @Test
  void shouldUnblockAllRequestsWhenJobsAvailable() throws Exception {
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
  void shouldCompleteAfterRequestTimeout() {
    // given
    final InflightActivateJobsRequest<JobActivationResult> longPollingRequest =
        getLongPollingJobActivationRequest();

    // when
    handler.internalActivateJobsRetry(longPollingRequest);
    waitUntil(longPollingRequest::hasScheduledTimer);
    actorClock.addTime(Duration.ofMillis(LONG_POLLING_TIMEOUT));
    waitUntil(longPollingRequest::isTimedOut);

    // then
    verify(longPollingRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  void shouldCompleteFollowingRequestsAfterTimeout() {
    // given
    final List<InflightActivateJobsRequest<JobActivationResult>> requests =
        activateJobsAndWaitUntilBlocked(FAILED_RESPONSE_THRESHOLD);
    actorClock.addTime(Duration.ofMillis(LONG_POLLING_TIMEOUT));
    requests.forEach(request -> waitUntil(request::isTimedOut));

    // when
    final InflightActivateJobsRequest<JobActivationResult> successRequest =
        getLongPollingJobActivationRequest();
    activateJobsStub.addAvailableJobs(TYPE, 1);
    brokerClient.notifyJobsAvailable(TYPE);
    handler.internalActivateJobsRetry(successRequest);
    Awaitility.await().until(successRequest::isCompleted);

    // then
    verify(successRequest.getResponseObserver(), times(1)).onNext(any());
    verify(successRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  void shouldNotBlockOtherJobTypes() {
    // given
    final String otherType = "other-type";
    activateJobsStub.addAvailableJobs(otherType, 2);
    activateJobsAndWaitUntilBlocked(FAILED_RESPONSE_THRESHOLD);

    // when
    final InflightActivateJobsRequest<JobActivationResult> otherRequest =
        getLongPollingJobActivationRequest(otherType, MAX_JOBS_TO_ACTIVATE);
    handler.internalActivateJobsRetry(otherRequest);
    Awaitility.await().until(otherRequest::isCompleted);

    // then
    verify(otherRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  void shouldReturnAllCollectedJobsIfDeliveredInChunks() {
    // given
    final int availableJobs = 10;
    activateJobsStub.addAvailableJobs(TYPE, 5);
    registerCustomHandlerWithNotification((r) -> activateJobsStub.addAvailableJobs(TYPE, 5));

    // when
    final InflightActivateJobsRequest<JobActivationResult> request =
        getLongPollingJobActivationRequest(TYPE, availableJobs);
    handler.internalActivateJobsRetry(request);
    Awaitility.await().timeout(Duration.ofMinutes(5)).until(request::isCompleted);

    // then
    verify(request.getResponseObserver(), times(2)).onNext(any());
    verify(request.getResponseObserver(), times(1)).onCompleted();
    assertThat(
            ((InspectableJobActivationRequestResponseObserver) request.getResponseObserver())
                .getResponse()
                .getJobs())
        .hasSize(10);
  }

  @Test
  void shouldProbeIfNoNotificationReceived() throws Exception {
    // given
    final long probeTimeout = 2000;
    handler =
        LongPollingActivateJobsHandler.<JobActivationResult>newBuilder()
            .setBrokerClient(brokerClient)
            .setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setLongPollingTimeout(20000)
            .setProbeTimeoutMillis(probeTimeout)
            .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
            .setNoJobsReceivedExceptionProvider(RuntimeException::new)
            .setRequestCanceledExceptionProvider(RuntimeException::new)
            .setMetrics(LongPollingMetrics.noop())
            .build();
    submitActorToActivateJobs(handler);

    final InflightActivateJobsRequest<JobActivationResult> request =
        getLongPollingJobActivationRequest();
    handler.internalActivateJobsRetry(request);
    waitUntil(request::hasScheduledTimer);

    // when
    actorClock.addTime(Duration.ofMillis(probeTimeout));

    // then
    verify(activateJobsStub, timeout(2000).times(2 * partitionsCount)).handle(any());
  }

  @Test
  void shouldProbeNextRequestWhenBlockedRequestsTimedOut() throws Exception {
    // given
    final long longPollingTimeout = 2000;
    final long probeTimeout = 20000;
    handler =
        LongPollingActivateJobsHandler.<JobActivationResult>newBuilder()
            .setBrokerClient(brokerClient)
            .setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setLongPollingTimeout(longPollingTimeout)
            .setProbeTimeoutMillis(probeTimeout)
            .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
            .setNoJobsReceivedExceptionProvider(RuntimeException::new)
            .setRequestCanceledExceptionProvider(RuntimeException::new)
            .setMetrics(LongPollingMetrics.noop())
            .build();
    submitActorToActivateJobs(handler);

    final int threshold = FAILED_RESPONSE_THRESHOLD;
    final List<InflightActivateJobsRequest<JobActivationResult>> requests =
        activateJobsAndWaitUntilBlocked(threshold);

    actorClock.addTime(Duration.ofMillis(longPollingTimeout));
    requests.forEach(
        request -> verify(request.getResponseObserver(), timeout(3000).times(1)).onCompleted());

    // when
    actorClock.addTime(Duration.ofMillis(probeTimeout));
    Thread.sleep(100); // Give some time for the periodic probe to execute
    activateJobsAndWaitUntilBlocked(1);

    // then
    final int totalRequests = threshold + 1;
    verify(activateJobsStub, timeout(1000).times(totalRequests * partitionsCount)).handle(any());
  }

  @Test
  void shouldUseRequestSpecificTimeout() {
    final long requestTimeout = 1000;
    final JobActivationRequest request =
        new JobActivationRequest()
            .type(TYPE)
            .maxJobsToActivate(1)
            .requestTimeout(requestTimeout)
            .timeout(requestTimeout * 2);
    final InflightActivateJobsRequest<JobActivationResult> longPollingRequest =
        toInflightActivateJobsRequest(request);

    handler.internalActivateJobsRetry(longPollingRequest);
    waitUntil(longPollingRequest::hasScheduledTimer);
    actorClock.addTime(Duration.ofMillis(requestTimeout));
    waitUntil(longPollingRequest::isTimedOut);

    // then
    verify(longPollingRequest.getResponseObserver(), times(1)).onCompleted();
  }

  @Test
  void shouldUseLargeRequestTimeout() {
    // given
    final long requestTimeout = 50000;
    final InflightActivateJobsRequest<JobActivationResult> shortRequest =
        toInflightActivateJobsRequest(
            new JobActivationRequest()
                .type(TYPE)
                .maxJobsToActivate(1)
                .requestTimeout(requestTimeout)
                .timeout(requestTimeout * 2));

    final long longTimeout = 100000;
    final InflightActivateJobsRequest<JobActivationResult> longRequest =
        toInflightActivateJobsRequest(
            new JobActivationRequest()
                .type(TYPE)
                .maxJobsToActivate(1)
                .requestTimeout(longTimeout)
                .timeout(longTimeout * 2));

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
  void shouldNotBlockWhenNegativeTimeout() {
    // given
    final InflightActivateJobsRequest<JobActivationResult> request =
        toInflightActivateJobsRequest(
            new JobActivationRequest()
                .type(TYPE)
                .maxJobsToActivate(1)
                .requestTimeout(-1L)
                .timeout(PROBE_TIMEOUT));

    // when
    handler.internalActivateJobsRetry(request);
    Awaitility.await().until(request::isCompleted);

    // then
    verify(request.getResponseObserver(), times(1)).onCompleted();
    assertThat(request.hasScheduledTimer()).isFalse();
    assertThat(request.isTimedOut()).isFalse();
  }

  @Test
  void
      shouldRepeatJobActivationRequestAgainstBrokersIfNewJobsArriveWhileIteratingThroughBrokersTheFirstTime() {
    // given

    // a request with timeout
    final InflightActivateJobsRequest<JobActivationResult> request =
        toInflightActivateJobsRequest(
            new JobActivationRequest()
                .type(TYPE)
                .maxJobsToActivate(15)
                .requestTimeout(500L)
                .timeout(1000L));

    /* and a request handler that simulates the following:
           - on the first round no broker has any jobs
           - about midway through iterating the brokers one of the brokers that has already been
    visited reports new jobs being available
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
    final ArgumentCaptor<JobActivationResult> responseArgumentCaptor =
        ArgumentCaptor.forClass(JobActivationResult.class);
    verify(request.getResponseObserver()).onNext(responseArgumentCaptor.capture());

    final JobActivationResult response = responseArgumentCaptor.getValue();

    assertThat(response.getJobs()).hasSize(10);
  }

  @Test
  void
      shouldReturnResourceExhaustedErrorIfNoJobsAvailableAndSomeBrokersReturnResourceExhaustionResponse() {
    // given
    final InflightActivateJobsRequest<JobActivationResult> request =
        toInflightActivateJobsRequest(
            new JobActivationRequest()
                .type(TYPE)
                .maxJobsToActivate(15)
                .requestTimeout(500L)
                .timeout(1000L));

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
    verify(request.getResponseObserver(), never()).onNext(any());
    verify(request.getResponseObserver(), never()).onCompleted();

    assertThat(throwableCaptor.getValue()).isInstanceOf(ResponseStatusException.class);
    final ResponseStatusException exception = (ResponseStatusException) throwableCaptor.getValue();
    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void shouldReturnJobsIfSomeBrokersHaveJobsWhileOthersReturnResourceExhaustionResponse() {
    // given
    final InflightActivateJobsRequest<JobActivationResult> request =
        toInflightActivateJobsRequest(
            new JobActivationRequest()
                .type(TYPE)
                .maxJobsToActivate(15)
                .requestTimeout(500L)
                .timeout(1000L));

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
    final ArgumentCaptor<JobActivationResult> responseArgumentCaptor =
        ArgumentCaptor.forClass(JobActivationResult.class);
    verify(request.getResponseObserver()).onNext(responseArgumentCaptor.capture());

    final JobActivationResult response = responseArgumentCaptor.getValue();

    assertThat(response.getJobs()).hasSize(10);
  }

  @Test
  void shouldRepeatRequestOnlyOnce() throws Exception {
    // given
    // the first three requests activates jobs
    final var firstRequest = getLongPollingJobActivationRequest();
    final var secondRequest = getLongPollingJobActivationRequest();
    final var thirdRequest = getLongPollingJobActivationRequest();
    // the last request does not activate any jobs
    final var fourthRequest = getLongPollingJobActivationRequest();

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
  void shouldCancelTimerOnResourceExhausted() {
    // given
    final InflightActivateJobsRequest<JobActivationResult> request =
        getLongPollingJobActivationRequest();

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
    assertThat(throwableCaptor.getValue()).isInstanceOf(ResponseStatusException.class);

    assertThat(request.hasScheduledTimer()).isFalse();
  }

  @Test
  void shouldCancelTimerOnBrokerRejectionException() {
    // given
    final InflightActivateJobsRequest<JobActivationResult> request =
        getLongPollingJobActivationRequest();

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
  void shouldCompleteRequestImmediatelyDespiteNotification() throws Exception {
    // given
    final InflightActivateJobsRequest<JobActivationResult> request =
        toInflightActivateJobsRequest(
            new JobActivationRequest()
                .type(TYPE)
                .requestTimeout(-1L)
                .timeout(PROBE_TIMEOUT)
                .maxJobsToActivate(1));

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
  void shouldTimeOutRequestDespiteMultipleNotificationLoops() throws Exception {
    // given
    final var request = getLongPollingJobActivationRequest();

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
  void shouldNotContinueWithNextPartitionIfResponseIsNotSend() throws Exception {
    // given
    final var request =
        spy(
            toInflightActivateJobsRequest(
                new JobActivationRequest()
                    .type(TYPE)
                    .maxJobsToActivate(3 * MAX_JOBS_TO_ACTIVATE)
                    .requestTimeout(500L)
                    .timeout(1000L)));

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
  void shouldNotContinueWithNextPartitionIfResponseFailed() throws Exception {
    // given
    final var request =
        toInflightActivateJobsRequest(
            new JobActivationRequest()
                .type(TYPE)
                .maxJobsToActivate(3 * MAX_JOBS_TO_ACTIVATE)
                .requestTimeout(500L)
                .timeout(1000L));

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
  void shouldMakeAllActivatedJobReactivatableWhenJobsAreNotSend() throws Exception {
    // given
    activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);
    final var request = spy(getLongPollingJobActivationRequest());

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
  void shouldMakeAllActivatedJobReactivatableWhenJobsAreNotSendDueException() throws Exception {
    // given
    activateJobsStub.addAvailableJobs(TYPE, MAX_JOBS_TO_ACTIVATE);
    final var request = getLongPollingJobActivationRequest();

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
  void shouldOnlyMakeJobsReactivatableInCurrentIterationWhenJobsAreNotReturned() throws Exception {
    // given
    final var responseNotSent = Either.right(false);

    final var request =
        spy(
            toInflightActivateJobsRequest(
                new JobActivationRequest()
                    .type(TYPE)
                    .maxJobsToActivate(3 * MAX_JOBS_TO_ACTIVATE)
                    .requestTimeout(500L)
                    .timeout(1000L)));

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
  void shouldOnlyMakeJobsReactivatableInCurrentIterationWhenJobsAreNotReturnedDueException()
      throws Exception {
    // given
    final var request =
        toInflightActivateJobsRequest(
            new JobActivationRequest()
                .type(TYPE)
                .maxJobsToActivate(3 * MAX_JOBS_TO_ACTIVATE)
                .requestTimeout(500L)
                .timeout(1000L));

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
  void shouldSetCurrentRetriesAndNoBackoff() {
    // given
    final var activatedJobRef = new AtomicReference<ActivatedJobResult>();
    activateJobsStub.addAvailableJobs(TYPE, 1);
    final var restRequest =
        new JobActivationRequest()
            .type(TYPE)
            .maxJobsToActivate(MAX_JOBS_TO_ACTIVATE)
            .requestTimeout(500L)
            .timeout(1000L);
    final var requestMappingResult = RequestMapper.toJobsActivationRequest(restRequest, false);
    if (requestMappingResult.isLeft()) {
      fail("REST Request mapping failed unexpectedly: " + requestMappingResult.getLeft());
    }
    final ActivateJobsRequest activateJobsRequest = requestMappingResult.get();
    final var brokerRequest =
        new BrokerActivateJobsRequest(activateJobsRequest.type())
            .setMaxJobsToActivate(activateJobsRequest.maxJobsToActivate())
            .setTimeout(activateJobsRequest.timeout())
            .setTenantIds(activateJobsRequest.tenantIds())
            .setVariables(activateJobsRequest.fetchVariable())
            .setWorker(activateJobsRequest.worker());
    final var request =
        new InflightActivateJobsRequest<>(
            getNextRequestId(),
            brokerRequest,
            spy(new JobActivationRequestResponseObserver(new CompletableFuture<>())),
            activateJobsRequest.requestTimeout()) {

          @Override
          public Either<Exception, Boolean> tryToSendActivatedJobs(
              final JobActivationResult grpcResponse) {
            activatedJobRef.set(grpcResponse.getJobs().getFirst());
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

    assertThat(String.valueOf(failRequest.getKey())).isEqualTo(activatedJob.getJobKey());
    assertThat(brokerRequestValue.getRetries()).isEqualTo(activatedJob.getRetries());
    assertThat(brokerRequestValue.getRetryBackoff()).isEqualTo(0);
    assertThat(brokerRequestValue.getErrorMessageBuffer()).isNotNull();
  }

  private List<InflightActivateJobsRequest<JobActivationResult>> activateJobsAndWaitUntilBlocked(
      final int amount) {
    return IntStream.range(0, amount)
        .boxed()
        .map(
            i -> {
              final InflightActivateJobsRequest<JobActivationResult> request =
                  getLongPollingJobActivationRequest();
              handler.internalActivateJobsRetry(request);
              waitUntil(request::hasScheduledTimer);
              return request;
            })
        .collect(Collectors.toList());
  }

  private InflightActivateJobsRequest<JobActivationResult> getLongPollingJobActivationRequest() {
    return getLongPollingJobActivationRequest(TYPE, MAX_JOBS_TO_ACTIVATE);
  }

  private InflightActivateJobsRequest<JobActivationResult> getLongPollingJobActivationRequest(
      final String jobType, final int maxJobsToActivate) {
    return toInflightActivateJobsRequest(
        new JobActivationRequest()
            .type(jobType)
            .maxJobsToActivate(maxJobsToActivate)
            .timeout(1L)
            .requestTimeout(0L));
  }

  private InflightActivateJobsRequest<JobActivationResult> toInflightActivateJobsRequest(
      final JobActivationRequest restRequest) {
    final var requestMappingResult = RequestMapper.toJobsActivationRequest(restRequest, false);
    if (requestMappingResult.isLeft()) {
      fail("REST Request mapping failed unexpectedly: " + requestMappingResult.getLeft());
    }
    final ActivateJobsRequest activateJobsRequest = requestMappingResult.get();
    final var brokerRequest =
        new BrokerActivateJobsRequest(activateJobsRequest.type())
            .setMaxJobsToActivate(activateJobsRequest.maxJobsToActivate())
            .setTimeout(activateJobsRequest.timeout())
            .setTenantIds(activateJobsRequest.tenantIds())
            .setVariables(activateJobsRequest.fetchVariable())
            .setWorker(activateJobsRequest.worker());
    return new InflightActivateJobsRequest<>(
        getNextRequestId(),
        brokerRequest,
        spy(new InspectableJobActivationRequestResponseObserver(new CompletableFuture<>())),
        activateJobsRequest.requestTimeout());
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
      final LongPollingActivateJobsHandler<JobActivationResult> handler) {
    final var future = new CompletableFuture<>();
    final var actor =
        Actor.newActor()
            .name("LongPollingHandler-Test")
            .actorStartedHandler(handler.andThen(future::complete))
            .build();
    actorScheduler.submitActor(actor);
    future.join();
  }

  private static class InspectableJobActivationRequestResponseObserver
      extends JobActivationRequestResponseObserver {

    public InspectableJobActivationRequestResponseObserver(
        final CompletableFuture<ResponseEntity<Object>> result) {
      super(result);
    }

    public JobActivationResult getResponse() {
      return response;
    }
  }
}
