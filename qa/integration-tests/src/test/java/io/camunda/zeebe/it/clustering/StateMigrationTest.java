/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.it.util.GrpcClientRule;
import io.netty.util.NetUtil;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public class StateMigrationTest {

  private static final DataSize ATOMIX_SEGMENT_SIZE = DataSize.ofMegabytes(2);
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

  // regression test for https://github.com/camunda/zeebe/issues/14329
  @Test
  public void shouldMakeJobActivatableAfterMigrationAndBackoff() {
    // given
    final String jobType = "test";
    clientRule.createSingleJob(jobType);

    final var activateResponse =
        clientRule
            .getClient()
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send()
            .join();
    final var jobKey = activateResponse.getJobs().get(0).getKey();

    final Duration backoffTimeout = Duration.ofDays(1);
    clientRule
        .getClient()
        .newFailCommand(jobKey)
        .retries(1)
        .retryBackoff(backoffTimeout)
        .send()
        .join();

    // when
    // we restart the leader - and expect another node takes over
    // new leader has to run migration first before starting processing
    clusteringRule.restartBroker(clusteringRule.getLeaderForPartition(1).getNodeId());
    // increasing time so after job backoff timeout job should be marked activatable again
    clusteringRule.getClock().addTime(backoffTimeout.plus(backoffTimeout));

    // then
    Awaitility.await()
        .until(
            () ->
                clientRule
                    .getClient()
                    .newActivateJobsCommand()
                    .jobType(jobType)
                    .maxJobsToActivate(1)
                    .send()
                    .join(),
            r -> !activateResponse.getJobs().isEmpty());
  }
}
