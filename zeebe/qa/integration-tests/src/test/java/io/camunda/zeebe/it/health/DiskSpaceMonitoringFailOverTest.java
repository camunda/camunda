/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.springframework.util.unit.DataSize;

public class DiskSpaceMonitoringFailOverTest {
  private final Timeout testTimeout = Timeout.seconds(120);
  private final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          cfg -> {
            cfg.getData().setDiskUsageMonitoringInterval(Duration.ofSeconds(1));
          });
  private final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldPauseProcessorWhenNoDiskSpaceAvailableBeforeInstall()
      throws InterruptedException {
    // given
    final var leaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var followers =
        clusteringRule.getBrokers().stream()
            .filter(b -> b.getConfig().getCluster().getNodeId() != leaderId)
            .collect(Collectors.toList());

    followers.forEach(
        broker ->
            Awaitility.await()
                // If broker is not healthy, it can take upto one health check interval (60 seconds)
                // to determine if it is healthy again
                .timeout(Duration.ofSeconds(70))
                .untilAsserted(
                    () -> {
                      clusteringRule.getClock().addTime(Duration.ofMinutes(1));
                      assertThat(
                              clusteringRule.isBrokerHealthy(
                                  broker.getConfig().getCluster().getNodeId()))
                          .isTrue();
                    }));
    // When one of the follower becomes the new leader, it should be out of disk space already
    for (final Broker broker : followers) {
      waitUntilDiskSpaceNotAvailable(broker);
    }

    // when
    clusteringRule.stopBrokerAndAwaitNewLeader(leaderId);
    final var newLeaderId = clusteringRule.getLeaderForPartition(1).getNodeId();

    // Force rescan of healthcheck
    clusteringRule.getClock().addTime(Duration.ofSeconds(60));

    // then
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .untilAsserted(() -> assertThat(clusteringRule.isBrokerHealthy(newLeaderId)).isFalse());
    // should install StreamProcessor and immediately pause it
    Awaitility.await(
            "Stream processor of broker " + newLeaderId + " is expected to be in paused state.")
        .until(
            () ->
                clusteringRule
                    .getBroker(newLeaderId)
                    .getBrokerContext()
                    .getBrokerAdminService()
                    .getPartitionStatus()
                    .get(1)
                    .streamProcessorPhase(),
            p -> p == Phase.PAUSED);
  }

  private void waitUntilDiskSpaceNotAvailable(final Broker broker) throws InterruptedException {
    final var diskSpaceMonitor = broker.getBrokerContext().getDiskSpaceUsageMonitor();

    final CountDownLatch diskSpaceNotAvailable = new CountDownLatch(1);
    diskSpaceMonitor.addDiskUsageListener(
        new DiskSpaceUsageListener() {
          @Override
          public void onDiskSpaceNotAvailable() {
            diskSpaceNotAvailable.countDown();
          }

          @Override
          public void onDiskSpaceAvailable() {}
        });

    diskSpaceMonitor.setFreeDiskSpaceSupplier(() -> DataSize.ofGigabytes(0).toBytes());

    clusteringRule.getClock().addTime(Duration.ofSeconds(1));

    // when
    assertThat(diskSpaceNotAvailable.await(2, TimeUnit.SECONDS)).isTrue();
  }
}
