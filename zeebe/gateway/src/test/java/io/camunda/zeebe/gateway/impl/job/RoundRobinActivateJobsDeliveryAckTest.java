/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerErrorResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerJobBatchAcknowledgeRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerJobBatchRejectRequest;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult.ActivatedJob;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.util.unit.DataSize;

final class RoundRobinActivateJobsDeliveryAckTest {

  private static final String TYPE = "delivery-ack-job";
  private static final long MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4).toBytes();

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();
  private RoundRobinActivateJobsHandler<Object> handler;
  private ActivateJobsStub activateJobsStub;

  @BeforeEach
  void setUp() {
    handler =
        new RoundRobinActivateJobsHandler<>(
            brokerClient,
            MAX_MESSAGE_SIZE,
            response ->
                new JobActivationResult<>() {
                  @Override
                  public int getJobsCount() {
                    return response.brokerResponse().getJobKeys().size();
                  }

                  @Override
                  public List<ActivatedJob> getJobs() {
                    return response.brokerResponse().getJobKeys().stream()
                        .map(key -> new ActivatedJob(key, 3))
                        .toList();
                  }

                  @Override
                  public Object getActivateJobsResponse() {
                    return response;
                  }

                  @Override
                  public List<ActivatedJob> getJobsToDefer() {
                    return Collections.emptyList();
                  }
                },
            RuntimeException::new);
    final var ready = new CompletableFuture<Void>();
    final var actor =
        Actor.newActor()
            .name("TestRoundRobinHandler")
            .actorStartedHandler(handler.andThen(ignored -> ready.complete(null)))
            .build();
    actorScheduler.submitActor(actor);
    actorScheduler.workUntilDone();
    ready.join();

    activateJobsStub = new ActivateJobsStub();
    activateJobsStub.registerWith(brokerClient);
    brokerClient.registerHandler(
        BrokerJobBatchAcknowledgeRequest.class,
        request -> new BrokerResponse<>(request.getRequestWriter(), request.getPartitionId(), 1L));
    brokerClient.registerHandler(
        BrokerJobBatchRejectRequest.class,
        request -> new BrokerResponse<>(request.getRequestWriter(), request.getPartitionId(), 1L));
  }

  @Test
  void shouldSetDeliveryAttemptKeyAndSendAckOnSuccess() {
    // given
    activateJobsStub.addAvailableJobs(TYPE, 1);
    final var completed = new AtomicBoolean(false);

    // when
    activate(completed);

    // then
    await().atMost(Duration.ofSeconds(2)).untilTrue(completed);

    final var activateRequests =
        brokerClient.getBrokerRequests().stream()
            .filter(BrokerActivateJobsRequest.class::isInstance)
            .map(BrokerActivateJobsRequest.class::cast)
            .toList();
    assertThat(activateRequests).isNotEmpty();
    assertThat(activateRequests.getFirst().getDeliveryAttemptKey()).isPositive();

    final var ackRequests =
        brokerClient.getBrokerRequests().stream()
            .filter(BrokerJobBatchAcknowledgeRequest.class::isInstance)
            .map(BrokerJobBatchAcknowledgeRequest.class::cast)
            .toList();
    assertThat(ackRequests).isNotEmpty();
    assertThat(ackRequests.getFirst().getRequestWriter().getDeliveryAttemptKey())
        .isEqualTo(activateRequests.getFirst().getDeliveryAttemptKey());
    assertThat(
            brokerClient.getBrokerRequests().stream()
                .anyMatch(BrokerJobBatchRejectRequest.class::isInstance))
        .isFalse();
  }

  @Test
  void shouldSendRejectOnTransportError() {
    // given
    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        request -> {
          throw new TimeoutException("connection closed");
        });
    final var completed = new AtomicBoolean(false);

    // when
    activate(completed);

    // then
    await().atMost(Duration.ofSeconds(2)).untilTrue(completed);

    final var rejectRequests =
        brokerClient.getBrokerRequests().stream()
            .filter(BrokerJobBatchRejectRequest.class::isInstance)
            .map(BrokerJobBatchRejectRequest.class::cast)
            .toList();
    // round-robin continues across partitions — at least one REJECT
    assertThat(rejectRequests).isNotEmpty();
    assertThat(
            brokerClient.getBrokerRequests().stream()
                .anyMatch(BrokerJobBatchAcknowledgeRequest.class::isInstance))
        .isFalse();
  }

  @Test
  void shouldNotSendRejectOnResourceExhausted() {
    // given
    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        request ->
            new BrokerErrorResponse<>(
                new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")));
    final var completed = new AtomicBoolean(false);

    // when
    activate(completed);

    // then
    await().atMost(Duration.ofSeconds(2)).untilTrue(completed);
    assertThat(
            brokerClient.getBrokerRequests().stream()
                .anyMatch(BrokerJobBatchRejectRequest.class::isInstance))
        .isFalse();
    assertThat(
            brokerClient.getBrokerRequests().stream()
                .anyMatch(BrokerJobBatchAcknowledgeRequest.class::isInstance))
        .isFalse();
  }

  private void activate(final AtomicBoolean completed) {
    final var request =
        new BrokerActivateJobsRequest(TYPE)
            .setMaxJobsToActivate(1)
            .setTimeout(10_000L)
            .setWorker("worker")
            .setVariables(Collections.emptyList())
            .setTenantIds(Collections.emptyList());

    handler.activateJobs(
        request,
        new ResponseObserver<>() {
          @Override
          public void onNext(final Object value) {}

          @Override
          public void onError(final Throwable t) {
            completed.set(true);
          }

          @Override
          public void onCompleted() {
            completed.set(true);
          }

          @Override
          public boolean isCancelled() {
            return false;
          }
        },
        cancel -> {},
        5_000L);

    // drain actor tasks including whenCompleteAsync callbacks
    for (int i = 0; i < 50; i++) {
      actorScheduler.workUntilDone();
    }
  }
}
