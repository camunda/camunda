/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import static io.zeebe.broker.clustering.atomix.AtomixFactory.GROUP_NAME;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCompleted;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.atomix.protocols.raft.storage.log.RaftLogReader;
import io.atomix.protocols.raft.storage.log.entry.RaftLogEntry;
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.JournalReader.Mode;
import io.zeebe.broker.Broker;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.client.api.response.BrokerInfo;
import io.zeebe.client.api.response.PartitionInfo;
import io.zeebe.client.api.worker.JobWorker;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: rewrite tests now that leader election is not controllable
public final class BrokerLeaderChangeTest {
  public static final String NULL_VARIABLES = null;
  public static final String JOB_TYPE = "testTask";
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

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

    clusteringRule.stopBroker(oldLeader);

    waitUntil(() -> clusteringRule.getLeaderForPartition(partition).getNodeId() != oldLeader);

    // when
    clusteringRule.restartBroker(oldLeader);

    // then

    final Stream<PartitionInfo> partitionInfo =
        clusteringRule.getTopologyFromClient().getBrokers().stream()
            .filter(b -> b.getNodeId() == oldLeader)
            .flatMap(b -> b.getPartitions().stream().filter(p -> p.getPartitionId() == partition));

    assertThat(partitionInfo.allMatch(p -> !p.isLeader())).isTrue();
  }

  @Test
  public void shouldChangeLeaderAfterLeaderDies() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(1);

    final long jobKey = clientRule.createSingleJob(JOB_TYPE);

    // when
    clusteringRule.stopBroker(leaderForPartition.getNodeId());
    final JobCompleter jobCompleter = new JobCompleter(jobKey);

    // then
    jobCompleter.waitForJobCompletion();

    jobCompleter.close();
  }

  @Test
  public void shouldHaveConsistentLogPositionsAfterRoleChanges() {
    // given
    final int firstLeader = clusteringRule.getLeaderForPartition(1).getNodeId();
    clientRule.createSingleJob(JOB_TYPE);
    final Logger log = LoggerFactory.getLogger("FINDME");
    log.info("First leader {}", firstLeader);

    stepDown(firstLeader);
    final int newLeader = clusteringRule.getLeaderForPartition(1).getNodeId();
    log.info("Second leader {}", newLeader);

    final int follower =
        clusteringRule.getBrokers().stream()
            .map(b -> b.getConfig().getCluster().getNodeId())
            .filter(b -> !Set.of(firstLeader, newLeader).contains(b))
            .findFirst()
            .get();
    log.info("Stopping follower {}", follower);
    clusteringRule.stopBroker(follower); // So we have better control on who is leader
    clientRule.createSingleJob(JOB_TYPE);

    // when
    // clusteringRule.stopBroker(newLeader.getNodeId());
    clusteringRule.getBroker(newLeader).close();
    clusteringRule.restartBrokerNoWait(follower); // Since follower's log is not uptodate

    log.info("Third leader {}", clusteringRule.getLeaderForPartition(1).getNodeId());
    assertThat(clusteringRule.getLeaderForPartition(1).getNodeId()).isEqualTo(firstLeader);

    clientRule.createSingleJob(JOB_TYPE);

    // then
    verifyConsistentLog(firstLeader);
  }

  private void verifyConsistentLog(final int nodeId) {

    final Broker broker = clusteringRule.getBroker(nodeId);
    final RaftPartition raftPartition =
        (RaftPartition)
            broker
                .getAtomix()
                .getPartitionService()
                .getPartitionGroup(GROUP_NAME)
                .getPartition(PartitionId.from(GROUP_NAME, 1));
    final RaftLogReader raftLogReader = raftPartition.getServer().openReader(-1, Mode.COMMITS);

    long prevPos = -1;
    while (raftLogReader.hasNext()) {
      final Indexed<RaftLogEntry> entry = raftLogReader.next();
      if (entry.type() == ZeebeEntry.class) {
        final ZeebeEntry zeebeEntry = (ZeebeEntry) entry.entry();
        assertThat(zeebeEntry.lowestPosition()).isGreaterThan(prevPos);
        prevPos = zeebeEntry.highestPosition();
      }
    }
  }

  private void stepDown(final int nodeId) {
    final Broker broker = clusteringRule.getBroker(nodeId);
    final RaftPartition raftPartition =
        (RaftPartition)
            broker
                .getAtomix()
                .getPartitionService()
                .getPartitionGroup(GROUP_NAME)
                .getPartition(PartitionId.from(GROUP_NAME, 1));
    raftPartition.stepDown().join();
    waitUntil(
        () -> {
          if (clusteringRule.getLeaderForPartition(1).getNodeId() == nodeId) {
            raftPartition.stepDown().join();
            return false;
          }
          return true;
        },
        500);
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
