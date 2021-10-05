/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.netty.util.NetUtil;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public final class RestoreTest {
  private static final DataSize ATOMIX_SEGMENT_SIZE = DataSize.ofMegabytes(2);
  private static final DataSize LARGE_PAYLOAD_BYTESIZE = DataSize.ofKilobytes(32);
  private static final String LARGE_PAYLOAD =
      "{\"blob\": \"" + getRandomBase64Bytes(LARGE_PAYLOAD_BYTESIZE.toBytes()) + "\"}";

  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);
  private final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          cfg -> {
            cfg.getData().setSnapshotPeriod(SNAPSHOT_PERIOD);
            cfg.getData().setLogSegmentSize(ATOMIX_SEGMENT_SIZE);
            cfg.getData().setLogIndexDensity(1);
            cfg.getNetwork().setMaxMessageSize(ATOMIX_SEGMENT_SIZE);
          });
  private final GrpcClientRule clientRule =
      new GrpcClientRule(
          config ->
              config
                  .gatewayAddress(NetUtil.toSocketAddressString(clusteringRule.getGatewayAddress()))
                  .defaultRequestTimeout(Duration.ofMinutes(1))
                  .usePlaintext());

  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);

  @Test
  public void shouldReplicateLogEvents() {
    // given
    clusteringRule.stopBrokerAndAwaitNewLeader(2);

    final BpmnModelInstance firstProcess =
        Bpmn.createExecutableProcess("process-test1").startEvent().endEvent().done();

    final BpmnModelInstance secondProcess =
        Bpmn.createExecutableProcess("process-test2").startEvent().endEvent().done();

    final BpmnModelInstance thirdProcess =
        Bpmn.createExecutableProcess("process-test3").startEvent().endEvent().done();

    // when
    final long firstProcessDefinitionKey = clientRule.deployProcess(firstProcess);
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForSnapshotAtBroker(getLeader());

    final long secondProcessDefinitionKey = clientRule.deployProcess(secondProcess);

    writeManyEventsUntilAtomixLogIsCompactable();
    clusteringRule.waitForSnapshotAtBroker(
        clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId()));

    clusteringRule.restartBroker(2);
    clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(2));

    clusteringRule.stopBrokerAndAwaitNewLeader(1);

    final long thirdProcessDefinitionKey = clientRule.deployProcess(thirdProcess);

    writeManyEventsUntilAtomixLogIsCompactable();
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForSnapshotAtBroker(
        clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId()));

    clusteringRule.restartBroker(1);
    clusteringRule.stopBrokerAndAwaitNewLeader(0);

    // then
    // If restore did not happen, following processes won't be deployed
    assertThat(clientRule.createProcessInstance(firstProcessDefinitionKey)).isPositive();
    assertThat(clientRule.createProcessInstance(secondProcessDefinitionKey)).isPositive();
    assertThat(clientRule.createProcessInstance(thirdProcessDefinitionKey)).isPositive();
  }

  @Test
  public void shouldKeepPositionsConsistent() {
    // given
    writeManyEventsUntilAtomixLogIsCompactable();

    // when
    clusteringRule.restartBroker(clusteringRule.getLeaderForPartition(1).getNodeId());

    writeManyEventsUntilAtomixLogIsCompactable();

    // then
    final var leaderLogStream = clusteringRule.getLogStream(1);

    final var reader = leaderLogStream.newLogStreamReader().join();
    reader.seekToFirstEvent();
    assertThat(reader.hasNext()).isTrue();

    var previousPosition = -1L;
    while (reader.hasNext()) {
      final var position = reader.next().getPosition();
      assertThat(position).isGreaterThan(previousPosition);
      previousPosition = position;
    }
  }

  // Regression test added for https://github.com/zeebe-io/zeebe/issues/6438
  @Test
  public void shouldBecomeLeaderAfterRestoreFromSnapshot() {
    // given
    clusteringRule.stopBrokerAndAwaitNewLeader(2);
    writeManyEventsUntilAtomixLogIsCompactable();
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForSnapshotAtBroker(getLeader());

    // when

    // Bring the stopped broker back - So that it has receives the snapshot from the leader
    clusteringRule.restartBroker(2);
    clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(2));

    // writing more events after stopping 0 ensures that 2 will become leader since once we stop 1,
    // 2 will have the longest log between it and 0
    clusteringRule.stopBrokerAndAwaitNewLeader(0);
    publishMessage();

    clusteringRule.stopBroker(1);
    // restart broker without waiting for the topology
    clusteringRule.getBroker(0).start().join();

    // then
    Awaitility.await("New leader must be 2")
        .pollInterval(10, TimeUnit.SECONDS)
        .timeout(60, TimeUnit.SECONDS)
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(clusteringRule.getLeaderForPartition(1).getNodeId()).isEqualTo(2));

    publishMessage();
  }

  private Broker getLeader() {
    return clusteringRule.getBroker(
        clusteringRule.getLeaderForPartition(START_PARTITION_ID).getNodeId());
  }

  private void writeManyEventsUntilAtomixLogIsCompactable() {
    final int requiredInstances =
        (int) Math.floorDiv(ATOMIX_SEGMENT_SIZE.toBytes(), LARGE_PAYLOAD_BYTESIZE.toBytes()) + 1;
    IntStream.range(0, requiredInstances)
        .forEach(
            i ->
                clientRule
                    .getClient()
                    .newPublishMessageCommand()
                    .messageName(String.valueOf(i))
                    .correlationKey(String.valueOf(i))
                    .variables(LARGE_PAYLOAD)
                    .send()
                    .join());
  }

  private void publishMessage() {
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("test")
        .correlationKey("test")
        .variables(LARGE_PAYLOAD)
        .send()
        .join();
  }

  private static String getRandomBase64Bytes(final long size) {
    final byte[] bytes = new byte[(int) size];
    ThreadLocalRandom.current().nextBytes(bytes);

    return Base64.getEncoder().encodeToString(bytes);
  }
}
