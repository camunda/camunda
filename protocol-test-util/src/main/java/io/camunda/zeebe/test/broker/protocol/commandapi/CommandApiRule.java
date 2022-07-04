/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.commandapi;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.camunda.zeebe.test.util.TestUtil.doRepeatedly;
import static io.camunda.zeebe.test.util.TestUtil.waitUntil;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Member;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.test.broker.protocol.MsgPackHelper;
import io.camunda.zeebe.transport.ClientTransport;
import io.camunda.zeebe.transport.TransportFactory;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.agrona.collections.Int2ObjectHashMap;
import org.junit.rules.ExternalResource;

public final class CommandApiRule extends ExternalResource {

  private static final String DEFAULT_WORKER = "defaultWorker";

  private final int nodeId;
  private final Supplier<AtomixCluster> atomixSupplier;
  private MsgPackHelper msgPackHelper;
  private int defaultPartitionId = -1;
  private final Int2ObjectHashMap<PartitionTestClient> testPartitionClients =
      new Int2ObjectHashMap<>();
  private final ControlledActorClock controlledActorClock = new ControlledActorClock();
  private ActorScheduler scheduler;

  public CommandApiRule(final Supplier<AtomixCluster> atomixSupplier) {
    nodeId = 0;
    this.atomixSupplier = atomixSupplier;
  }

  @Override
  protected void before() {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setActorClock(controlledActorClock)
            .build();
    scheduler.start();

    msgPackHelper = new MsgPackHelper();

    waitForTopology();
    final List<Integer> partitionIds = doRepeatedly(this::getPartitionIds).until(p -> !p.isEmpty());
    defaultPartitionId = partitionIds.get(0);
  }

  @Override
  protected void after() {

    if (scheduler != null) {
      scheduler.stop();
    }
  }

  private void waitForTopology() {
    waitUntil(() -> getBrokerInfoStream().count() > 0);
  }

  /** targets the default partition by default */
  public ExecuteCommandRequestBuilder createCmdRequest() {
    return createCmdRequest(defaultPartitionId);
  }

  public ExecuteCommandRequestBuilder createCmdRequest(final int partition) {
    final var outputAdapter = createClientTransport();

    final var broker =
        getBrokerInfoStream().filter(brokerInfo -> brokerInfo.getNodeId() == nodeId).findFirst();

    if (broker.isPresent()) {
      final var brokerInfo = broker.get();

      return new ExecuteCommandRequestBuilder(
              outputAdapter, brokerInfo.getCommandApiAddress(), msgPackHelper)
          .partitionId(partition);
    }
    throw new IllegalStateException("Node " + nodeId + "not yet available");
  }

  private ClientTransport createClientTransport() {
    final var atomixCluster = fetchAtomix();
    return new TransportFactory(scheduler)
        .createClientTransport(atomixCluster.getMessagingService());
  }

  public PartitionTestClient partitionClient() {
    return partitionClient(defaultPartitionId);
  }

  public PartitionTestClient partitionClient(final int partitionId) {
    if (!testPartitionClients.containsKey(partitionId)) {
      testPartitionClients.put(partitionId, new PartitionTestClient(this, partitionId));
    }
    return testPartitionClients.get(partitionId);
  }

  public ExecuteCommandRequest activateJobs(
      final int partitionId,
      final String type,
      final long lockDuration,
      final int maxJobsToActivate) {
    // to make sure that job already exist
    partitionClient(partitionId)
        .receiveJobs()
        .withIntent(JobIntent.CREATED)
        .withType(type)
        .getFirst();

    return createCmdRequest(partitionId)
        .type(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE)
        .command()
        .put("type", type)
        .put("worker", DEFAULT_WORKER)
        .put("timeout", lockDuration)
        .put("maxJobsToActivate", maxJobsToActivate)
        .put("jobs", Collections.emptyList())
        .done()
        .send();
  }

  public ExecuteCommandRequest activateJobs(
      final int partitionId, final String type, final long lockDuration) {
    return activateJobs(partitionId, type, lockDuration, 10);
  }

  public List<Integer> getPartitionIds() {
    return getBrokerInfoStream()
        .findFirst()
        .map(
            brokerInfo ->
                IntStream.range(
                        START_PARTITION_ID, START_PARTITION_ID + brokerInfo.getPartitionsCount())
                    .boxed()
                    .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }

  private Stream<BrokerInfo> getBrokerInfoStream() {
    final AtomixCluster atomixCluster = fetchAtomix();

    return atomixCluster.getMembershipService().getMembers().stream()
        .map(Member::properties)
        .map(BrokerInfo::fromProperties)
        .filter(Objects::nonNull);
  }

  private AtomixCluster fetchAtomix() {
    final AtomixCluster atomixCluster = atomixSupplier.get();
    Objects.requireNonNull(atomixCluster);
    return atomixCluster;
  }
}
