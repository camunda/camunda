/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.health;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.protocol.Protocol;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class HealthMonitoringTest {
  public static final String JOB_TYPE = "testTask";
  private static final long SNAPSHOT_PERIOD_MINUTES = 5;
  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          1, 3, 3, cfg -> cfg.getData().setSnapshotPeriod(SNAPSHOT_PERIOD_MINUTES + "m"));
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldReportFailureWhenLeaderTransitionFailed() {
    // given
    final int partition = Protocol.START_PARTITION_ID;
    final int leaderNodeId = clusteringRule.getLeaderForPartition(partition).getNodeId();
    final Broker leader = clusteringRule.getBroker(leaderNodeId);
    final Collection<Broker> followers = new ArrayList<>(clusteringRule.getBrokers());
    followers.remove(leader);
    followers.forEach(follower -> assertThat(follower.isHealthy()).isTrue());

    // do some work to create a snapshot
    clientRule.createSingleJob(JOB_TYPE);
    clusteringRule.getClock().addTime(Duration.ofMinutes(SNAPSHOT_PERIOD_MINUTES));
    clusteringRule.waitForValidSnapshotAtBroker(leader);
    followers.forEach(clusteringRule::waitForValidSnapshotAtBroker);

    // when
    // corrupt snapshot on all followers because we cannot control which one will become leader
    followers.stream().forEach(this::corruptAllSnapshots);
    // cannot use clusteringRule.stopbroker() as it waits for new leader to be installed.
    leader.close();

    // then
    // TODO: Use http endpoint to query health status when it is available
    // https://github.com/zeebe-io/zeebe/issues/3833
    waitUntil(() -> followers.stream().anyMatch(follower -> !follower.isHealthy()));
  }

  private void corruptAllSnapshots(final Broker leader) {
    final File snapshotsDir = clusteringRule.getSnapshotsDirectory(leader);
    Arrays.stream(snapshotsDir.listFiles())
        .filter(File::isDirectory)
        .forEach(
            snapshot -> {
              final var filesInSnapshot = snapshot.listFiles();
              Arrays.stream(filesInSnapshot)
                  .filter(f -> f.getName().contains("MANIFEST"))
                  .forEach(file -> file.delete());
            });
  }
}
