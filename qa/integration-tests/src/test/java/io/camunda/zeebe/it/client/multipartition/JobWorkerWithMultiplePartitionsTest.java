/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.multipartition;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class JobWorkerWithMultiplePartitionsTest {

  private static final int PARTITION_COUNT = 3;

  private static final ClusteringRule CLUSTERING_RULE = new ClusteringRule(PARTITION_COUNT, 1, 1);
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(CLUSTERING_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(CLUSTERING_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldReceiveJobsFromMultiplePartitions() {
    // given
    final String jobType = helper.getJobType();

    final RecordingJobHandler handler = new RecordingJobHandler();

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
