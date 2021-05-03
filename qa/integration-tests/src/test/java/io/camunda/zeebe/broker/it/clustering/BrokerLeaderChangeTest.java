/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.it.clustering;

import static io.camunda.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCompleted;
import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.it.util.GrpcClientRule;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.protocol.Protocol;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

// FIXME: rewrite tests now that leader election is not controllable
public final class BrokerLeaderChangeTest {
  public static final String JOB_TYPE = "testTask";

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule = new ClusteringRule(1, 3, 3);
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldBecomeFollowerAfterRestartLeaderChange() {
    // given
    final int partition = Protocol.START_PARTITION_ID;
    final int oldLeader = clusteringRule.getLeaderForPartition(partition).getNodeId();

    clusteringRule.stopBrokerAndAwaitNewLeader(oldLeader);

    waitUntil(() -> clusteringRule.getLeaderForPartition(partition).getNodeId() != oldLeader);

    // when
    clusteringRule.restartBroker(oldLeader);

    // then

    final Stream<PartitionInfo> partitionInfo =
        clusteringRule.getTopologyFromClient().getBrokers().stream()
            .filter(b -> b.getNodeId() == oldLeader)
            .flatMap(b -> b.getPartitions().stream().filter(p -> p.getPartitionId() == partition));

    assertThat(partitionInfo).noneMatch(PartitionInfo::isLeader);
  }

  @Test
  public void shouldChangeLeaderAfterLeaderDies() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(1);

    final long jobKey = clientRule.createSingleJob(JOB_TYPE);

    // when
    clusteringRule.stopBrokerAndAwaitNewLeader(leaderForPartition.getNodeId());
    final JobCompleter jobCompleter = new JobCompleter(jobKey);

    // then
    jobCompleter.waitForJobCompletion();

    jobCompleter.close();
  }

  @Test
  public void shouldBeAbleToBecomeLeaderAgain() {
    // given
    final var firstLeaderInfo = clusteringRule.getLeaderForPartition(1);
    final var firstLeaderNodeId = firstLeaderInfo.getNodeId();

    // when
    // restarting until we become leader again
    do {
      final var previousLeader = clusteringRule.getCurrentLeaderForPartition(1);
      clusteringRule.stepDown(previousLeader.getNodeId(), 1);
      Awaitility.await()
          .pollInterval(Duration.ofMillis(100))
          .atMost(Duration.ofSeconds(10))
          .until(
              () -> clusteringRule.getCurrentLeaderForPartition(1),
              newLeader -> !previousLeader.equals(newLeader));

    } while (clusteringRule.getCurrentLeaderForPartition(1).getNodeId() != firstLeaderNodeId);

    // then
    assertThat(clusteringRule.getCurrentLeaderForPartition(1).getNodeId())
        .isEqualTo(firstLeaderNodeId);
  }

  class JobCompleter {
    private final JobWorker jobWorker;
    private final CountDownLatch latch = new CountDownLatch(1);

    JobCompleter(final long jobKey) {

      jobWorker =
          clientRule
              .getClient()
              .newWorker()
              .jobType(JOB_TYPE)
              .handler(
                  (client, job) -> {
                    if (job.getKey() == jobKey) {
                      client.newCompleteCommand(job.getKey()).send();
                      latch.countDown();
                    }
                  })
              .open();
    }

    void waitForJobCompletion() {
      try {
        latch.await(10, TimeUnit.SECONDS);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
      assertJobCompleted();
    }

    void close() {
      if (!jobWorker.isClosed()) {
        jobWorker.close();
      }
    }
  }
}
