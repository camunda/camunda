/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.Broker;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerFailedLeaderChangeTest {
  private static final String JOB_TYPE = "testTask";
  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);
  private final Timeout testTimeout = Timeout.seconds(120);
  private final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          cfg -> {
            cfg.getData().setSnapshotPeriod(SNAPSHOT_PERIOD);
            cfg.getData().setLogIndexDensity(1);
          });
  private final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldStepDownWhenLeaderTransitionFailed() throws InterruptedException {
    // given
    final int partition = START_PARTITION_ID;
    final int leaderNodeId = clusteringRule.getLeaderForPartition(partition).getNodeId();
    final Broker leader = clusteringRule.getBroker(leaderNodeId);
    final Collection<Broker> followers = new ArrayList<>(clusteringRule.getBrokers());
    followers.remove(leader);

    // do some work to create a snapshot
    clientRule.createSingleJob(JOB_TYPE);
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForSnapshotAtBroker(leader);
    followers.forEach(clusteringRule::waitForSnapshotAtBroker);

    // when
    // corrupt snapshot on all followers because we cannot control which one will become leader
    followers.forEach(this::corruptAllSnapshots);
    final CountDownLatch followerInstall = new CountDownLatch(1);
    followers.stream().forEach(b -> b.addPartitionListener(new InstallListener(followerInstall)));
    // cannot use clusteringRule.stopbroker() as it waits for new leader to be installed.
    leader.close();

    // then
    // steps down after leader install fails and install follower services
    followerInstall.await();
    assertThat(followerInstall.getCount()).isZero();
  }

  private void corruptAllSnapshots(final Broker leader) {
    // corrupt snapshot to fail leader installation
    final File snapshotsDir = clusteringRule.getSnapshotsDirectory(leader);
    Arrays.stream(snapshotsDir.listFiles())
        .filter(File::isDirectory)
        .forEach(
            snapshot -> {
              final var filesInSnapshot = snapshot.listFiles();
              Arrays.stream(filesInSnapshot)
                  .filter(f -> f.getName().contains("MANIFEST"))
                  .forEach(File::delete);
            });
  }

  private static final class InstallListener implements PartitionListener {

    final CountDownLatch followerInstall;

    private InstallListener(final CountDownLatch followerInstall) {
      this.followerInstall = followerInstall;
    }

    @Override
    public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
      followerInstall.countDown();
      return CompletableActorFuture.completedExceptionally(
          new RuntimeException("fail follower installation"));
    }

    @Override
    public ActorFuture<Void> onBecomingLeader(
        final int partitionId, final long term, final LogStream logStream) {
      return new CompletableActorFuture<>();
    }
  }
}
