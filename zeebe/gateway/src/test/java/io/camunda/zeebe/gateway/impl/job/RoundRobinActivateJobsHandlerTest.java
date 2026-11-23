/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler.MAX_TRACKED_JOB_TYPES;
import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@NullMarked
final class RoundRobinActivateJobsHandlerTest {

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();
  // The production cache configuration, so the test verifies the real bound; cleanUp() below
  // applies pending evictions regardless of which executor the cache runs maintenance on.
  private final Cache<String, RequestDispatchStrategy> jobTypeCache =
      RoundRobinActivateJobsHandler.newJobTypeCache();
  private final RoundRobinActivateJobsHandler<Object> handler =
      new RoundRobinActivateJobsHandler<>(
          brokerClient,
          Long.MAX_VALUE,
          r -> new EmptyResult(),
          IllegalStateException::new,
          // The deterministic factory makes every untracked type start at the first partition, so
          // the assertion below can tell an evicted type (restarts there) from a kept one
          // (advances); the production factory starts untracked types at a random partition.
          RoundRobinDispatchStrategy::new,
          jobTypeCache);

  @Test
  void shouldForgetRoundRobinPositionOfJobTypesBeyondLimit() {
    // given - every job type activated once, which advances its round-robin cursor
    final List<Integer> polledPartitions = new ArrayList<>();
    brokerClient.registerHandler(
        BrokerActivateJobsRequest.class,
        (BrokerActivateJobsRequest request) -> {
          polledPartitions.add(request.getPartitionId());
          return new BrokerResponse<>(new JobBatchRecord(), request.getPartitionId(), 1L);
        });
    actorScheduler.submitActor(Actor.newActor().actorStartedHandler(handler).build());
    actorScheduler.workUntilDone();

    final int jobTypesCount = MAX_TRACKED_JOB_TYPES * 2;
    for (int i = 0; i < jobTypesCount; i++) {
      assertThat(firstPolledPartition("type-" + i, polledPartitions)).isEqualTo(START_PARTITION_ID);
    }

    // when
    int restartedJobTypes = 0;
    for (int i = 0; i < jobTypesCount; i++) {
      if (firstPolledPartition("type-" + i, polledPartitions) == START_PARTITION_ID) {
        restartedJobTypes++;
      }
    }

    // then - at most MAX_TRACKED_JOB_TYPES kept their cursor; all others started over
    assertThat(restartedJobTypes).isGreaterThanOrEqualTo(jobTypesCount - MAX_TRACKED_JOB_TYPES);
  }

  private int firstPolledPartition(final String jobType, final List<Integer> polledPartitions) {
    polledPartitions.clear();
    final var brokerRequest = new BrokerActivateJobsRequest(jobType).setMaxJobsToActivate(1);
    brokerRequest.setPartitionGroup(DEFAULT_PHYSICAL_TENANT_ID);
    final var request =
        new InflightActivateJobsRequest<Object>(1, brokerRequest, new NoopResponseObserver(), 1000);
    handler.activateJobs(
        brokerClient
            .getTopologyManager()
            .getTopology(DEFAULT_PHYSICAL_TENANT_ID)
            .getPartitionsCount(),
        request,
        e -> {},
        (a, b) -> {});
    actorScheduler.workUntilDone();
    jobTypeCache.cleanUp();
    return polledPartitions.getFirst();
  }

  private static final class EmptyResult implements JobActivationResult<Object> {

    @Override
    public int getJobsCount() {
      return 0;
    }

    @Override
    public List<ActivatedJob> getJobs() {
      return List.of();
    }

    @Override
    public Object getActivateJobsResponse() {
      return new Object();
    }

    @Override
    public List<ActivatedJob> getJobsToDefer() {
      return List.of();
    }
  }

  private static final class NoopResponseObserver implements ResponseObserver<Object> {

    @Override
    public void onCompleted() {}

    @Override
    public void onNext(final Object element) {}

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void onError(final Throwable throwable) {}
  }
}
