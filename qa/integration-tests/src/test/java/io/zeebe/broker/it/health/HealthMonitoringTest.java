/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.health;

import static io.zeebe.broker.clustering.atomix.AtomixFactory.GROUP_NAME;
import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.partition.RaftPartition;
import io.zeebe.broker.Broker;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.LangUtil;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class HealthMonitoringTest {
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
  public void shouldReportUnhealthyWhenLeaderTransitionFailed() {
    // given
    final int partition = START_PARTITION_ID;
    final int leaderNodeId = clusteringRule.getLeaderForPartition(partition).getNodeId();
    final Broker leader = clusteringRule.getBroker(leaderNodeId);
    final Collection<Broker> followers = new ArrayList<>(clusteringRule.getBrokers());
    followers.remove(leader);
    followers.forEach(follower -> assertThat(isBrokerHealthy(follower)).isTrue());

    // do some work to create a snapshot
    clientRule.createSingleJob(JOB_TYPE);
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
    clusteringRule.waitForSnapshotAtBroker(leader);
    followers.forEach(clusteringRule::waitForSnapshotAtBroker);

    // when
    // corrupt snapshot on all followers because we cannot control which one will become leader
    followers.forEach(this::corruptAllSnapshots);
    followers.forEach(b -> b.addPartitionListener(new FailingPartitionListener()));
    // cannot use clusteringRule.stopbroker() as it waits for new leader to be installed.
    leader.close();

    // then
    waitUntil(() -> followers.stream().anyMatch(follower -> !isBrokerHealthy(follower)));
  }

  @Test
  public void shouldReportUnhealthyWhenRaftInactive() {
    // given
    final int partition = START_PARTITION_ID;
    final int leaderNodeId = clusteringRule.getLeaderForPartition(partition).getNodeId();
    final Broker leader = clusteringRule.getBroker(leaderNodeId);
    assertThat(isBrokerHealthy(leader)).isTrue();

    // when
    final RaftPartition raftPartition =
        (RaftPartition)
            leader
                .getAtomix()
                .getPartitionService()
                .getPartitionGroup(GROUP_NAME)
                .getPartition(PartitionId.from(GROUP_NAME, START_PARTITION_ID));
    raftPartition.getServer().stop();

    // then
    waitUntil(() -> !isBrokerHealthy(leader));
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

  private boolean isBrokerHealthy(final Broker broker) {
    final var monitoringApi = broker.getConfig().getNetwork().getMonitoringApi();
    final var host = monitoringApi.getHost();
    final var port = monitoringApi.getPort();
    final var uri = URI.create(String.format("http://%s:%d/health", host, port));
    final var client = HttpClient.newHttpClient();
    final var request = HttpRequest.newBuilder(uri).build();
    try {
      return client.send(request, BodyHandlers.discarding()).statusCode() == 204;
    } catch (final InterruptedException | IOException e) {
      LangUtil.rethrowUnchecked(e);
      return false; // should not happen
    }
  }

  private static class FailingPartitionListener implements PartitionListener {

    @Override
    public ActorFuture<Void> onBecomingFollower(
        final int partitionId, final long term, final LogStream logStream) {
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
