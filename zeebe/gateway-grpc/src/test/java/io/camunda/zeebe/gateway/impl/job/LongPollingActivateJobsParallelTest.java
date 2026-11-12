/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.job.FailJobStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.grpc.ServerStreamObserver;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.metrics.LongPollingMetricsFactory;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

/** Tests job activation across partitions with parallelActivation enabled. */
final class LongPollingActivateJobsParallelTest {

  private static final String TYPE = "test";
  private static final long MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4).toBytes();

  @RegisterExtension final ActorSchedulerExtension actorScheduler = new ActorSchedulerExtension();

  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();
  private final AtomicLong requestIdGenerator = new AtomicLong(1);
  private final PerPartitionActivateJobsStub stub = new PerPartitionActivateJobsStub();
  private final FailJobStub failJobStub = spy(new FailJobStub());
  private LongPollingActivateJobsHandler<ActivateJobsResponse> handler;
  private int partitionsCount;

  @BeforeEach
  void setup() {
    handler =
        LongPollingActivateJobsHandler.<ActivateJobsResponse>newBuilder()
            .setBrokerClient(brokerClient)
            .setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setLongPollingTimeout(5000)
            .setProbeTimeoutMillis(20000)
            .setMinEmptyResponses(3)
            .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
            .setResourceExhaustedExceptionProvider(Gateway.RESOURCE_EXHAUSTED_EXCEPTION_PROVIDER)
            .setRequestCanceledExceptionProvider(Gateway.REQUEST_CANCELED_EXCEPTION_PROVIDER)
            .setMetricsFactory(LongPollingMetricsFactory.noop())
            .setParallelActivation(true)
            .build();
    final var started = new CompletableFuture<ActorControl>();
    actorScheduler.submitActor(
        Actor.newActor()
            .name("LongPollingHandler-Test")
            .actorStartedHandler(handler.andThen(started::complete))
            .build());
    started.join();

    stub.addAvailableJobs(TYPE, Integer.MAX_VALUE);
    brokerClient.registerHandler(BrokerActivateJobsRequest.class, stub);
    failJobStub.registerWith(brokerClient);
    partitionsCount = brokerClient.getTopologyManager().getTopology().getPartitionsCount();
  }

  @Test
  void shouldActivateJobsInOneRoundWhenAllPartitionsHaveJobs() {
    // given
    final var jobsPerPartition = 2;
    final var request = request(partitionsCount * jobsPerPartition);
    IntStream.rangeClosed(1, partitionsCount).forEach(p -> stub.setAvailable(p, 100));

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::isCompleted);
    assertThat(stub.asks())
        .describedAs("every partition is asked once, for an equal share")
        .containsExactlyInAnyOrderElementsOf(
            IntStream.rangeClosed(1, partitionsCount)
                .mapToObj(p -> new Ask(p, jobsPerPartition))
                .toList());
    assertThat(activatedJobs(request)).isEqualTo(partitionsCount * jobsPerPartition);
  }

  @Test
  void shouldAskPartitionWithJobsAgainAndNotTheEmptyOnes() {
    // given
    final var maxJobs = partitionsCount * 3;
    final var request = request(maxJobs);
    stub.setAvailable(2, 100);

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::isCompleted);
    assertThat(activatedJobs(request)).isEqualTo(maxJobs);
    assertThat(stub.asks())
        .describedAs("the empty partitions are asked once, the partition with jobs until done")
        .filteredOn(ask -> ask.partitionId() != 2)
        .hasSize(partitionsCount - 1);
    assertThat(stub.asks()).filteredOn(ask -> ask.partitionId() == 2).hasSizeGreaterThan(1);
  }

  @Test
  void shouldAskPartitionsHintedByBrokerForMostJobs() {
    // given
    final var maxJobs = partitionsCount * 3;
    final var request = request(maxJobs);
    stub.setAvailable(2, 100);
    brokerClient.notifyJobsAvailable(
        DEFAULT_PHYSICAL_TENANT_ID + "-jobsAvailable-partition", "2:" + TYPE);

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::isCompleted);
    assertThat(activatedJobs(request)).isEqualTo(maxJobs);
    assertThat(stub.asks())
        .describedAs("the hinted partition is asked for all but one job per other partition")
        .contains(new Ask(2, maxJobs - (partitionsCount - 1)));
  }

  @Test
  void shouldCompleteWithFewerJobsWhenPartitionsRunOut() {
    // given
    final var request = request(partitionsCount * 3);
    stub.setAvailable(1, 2);

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::isCompleted);
    assertThat(activatedJobs(request)).isEqualTo(2);
    assertThat(stub.asks()).hasSize(partitionsCount);
  }

  @Test
  void shouldParkRequestWhenNoPartitionHasJobs() {
    // given
    final var request = request(partitionsCount * 3);

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::hasScheduledTimer);
    assertThat(request.isCompleted()).isFalse();
    assertThat(stub.asks()).hasSize(partitionsCount);
  }

  @Test
  void shouldActivateOneJobFromFirstPartitionThatHasOne() {
    // given
    final var request = request(1);
    stub.setAvailable(partitionsCount, 5);

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::isCompleted);
    assertThat(activatedJobs(request)).isEqualTo(1);
    assertThat(stub.asks()).allMatch(ask -> ask.maxJobs() == 1);
  }

  @Test
  void shouldGiveBackJobsBeyondWhatTheRequestAskedFor() {
    // given
    final var request = request(1);
    IntStream.rangeClosed(1, partitionsCount).forEach(p -> stub.setAvailable(p, 5));

    // when
    handler.internalActivateJobsRetry(request);

    // then
    waitUntil(request::isCompleted);
    assertThat(activatedJobs(request)).isEqualTo(1);
    Awaitility.await()
        .untilAsserted(() -> verify(failJobStub, times(partitionsCount - 1)).handle(any()));
  }

  private int activatedJobs(final InflightActivateJobsRequest<ActivateJobsResponse> request) {
    final var captor = ArgumentCaptor.forClass(ActivateJobsResponse.class);
    verify(request.getResponseObserver(), atLeast(0)).onNext(captor.capture());
    return captor.getAllValues().stream().mapToInt(ActivateJobsResponse::getJobsCount).sum();
  }

  private InflightActivateJobsRequest<ActivateJobsResponse> request(final int maxJobs) {
    final var grpcRequest =
        ActivateJobsRequest.newBuilder().setType(TYPE).setMaxJobsToActivate(maxJobs).build();
    final var brokerRequest = RequestMapper.toActivateJobsRequest(grpcRequest);
    brokerRequest.setPartitionGroup(DEFAULT_PHYSICAL_TENANT_ID);
    return new InflightActivateJobsRequest<>(
        requestIdGenerator.getAndIncrement(),
        brokerRequest,
        spy(ServerStreamObserver.class),
        grpcRequest.getRequestTimeout());
  }

  record Ask(int partitionId, int maxJobs) {}

  /** Serves a configurable number of jobs per partition and remembers every ask it received. */
  private static final class PerPartitionActivateJobsStub extends ActivateJobsStub {
    private final Map<Integer, Integer> availablePerPartition = new ConcurrentHashMap<>();
    private final List<Ask> asks = new CopyOnWriteArrayList<>();

    void setAvailable(final int partitionId, final int jobs) {
      availablePerPartition.put(partitionId, jobs);
    }

    List<Ask> asks() {
      return asks;
    }

    @Override
    public BrokerResponse<JobBatchRecord> handle(final BrokerActivateJobsRequest request)
        throws Exception {
      final var partitionId = request.getPartitionId();
      final var asked = request.getRequestWriter().getMaxJobsToActivate();
      asks.add(new Ask(partitionId, asked));

      final var available = availablePerPartition.getOrDefault(partitionId, 0);
      final var served = Math.min(asked, available);
      availablePerPartition.put(partitionId, available - served);
      request.setMaxJobsToActivate(served);
      return super.handle(request);
    }
  }
}
