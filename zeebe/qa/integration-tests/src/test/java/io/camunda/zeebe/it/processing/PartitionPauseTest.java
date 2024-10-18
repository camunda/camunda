/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.processing;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public final class PartitionPauseTest {
  private static final int PARTITION_COUNT = 3;

  private static final ClusteringRule CLUSTERING_RULE = new ClusteringRule(PARTITION_COUNT, 3, 3);
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(CLUSTERING_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(CLUSTERING_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldReturnPartitionUnavailableWithPausedPartition() {
    // given
    final String jobType = helper.getJobType();

    // pause first partition
    final var firstPartitionLeader =
        CLUSTERING_RULE.getLeaderForPartition(Protocol.DEPLOYMENT_PARTITION).getNodeId();

    final var firstPartitionAdminService =
        CLUSTERING_RULE.getBroker(firstPartitionLeader).getBrokerContext().getBrokerAdminService();
    firstPartitionAdminService.pauseStreamProcessing();

    System.out.println("Sending jobs to all partitions...");
    Assert.assertThrows(
        "Processing paused for partition '1'",
        ClientStatusException.class,
        () -> CLIENT_RULE.createSingleJob(jobType));

    // TODO: assert log contains debug message
    // DEBUG io.camunda.zeebe.gateway - Partition is currently unavailable:
    // BrokerError{code=PARTITION_UNAVAILABLE, message='Processing paused for partition '1''}
  }

  @Test
  public void
      shouldReturnPartitionUnavailableWithPausedPartitionAndContinueProcessingAfterResume() {
    // given
    final String jobType = helper.getJobType();

    final RecordingJobHandler handler = new RecordingJobHandler();

    // pause first partition
    final var firstPartitionLeader =
        CLUSTERING_RULE.getLeaderForPartition(Protocol.DEPLOYMENT_PARTITION).getNodeId();

    final var firstPartitionAdminService =
        CLUSTERING_RULE.getBroker(firstPartitionLeader).getBrokerContext().getBrokerAdminService();
    firstPartitionAdminService.pauseStreamProcessing();

    System.out.println("Sending jobs to all partitions...");
    Assert.assertThrows(
        "Processing paused for partition '1'",
        ClientStatusException.class,
        () -> CLIENT_RULE.createSingleJob(jobType));

    // TODO: assert log contains debug message

    firstPartitionAdminService.resumeStreamProcessing();

    IntStream.range(0, PARTITION_COUNT).forEach(p -> CLIENT_RULE.createSingleJob(jobType));

    // when
    CLIENT_RULE.getClient().newWorker().jobType(jobType).handler(handler).name("test").open();

    // then
    waitUntil(() -> handler.getHandledJobs().size() >= PARTITION_COUNT);

    final List<Integer> receivedPartitionIds =
        handler.getHandledJobs().stream()
            .map(ActivatedJob::getKey)
            .map(Protocol::decodePartitionId)
            .collect(Collectors.toList());

    assertThat(receivedPartitionIds)
        .containsExactlyInAnyOrderElementsOf(CLIENT_RULE.getPartitions());
  }
}
