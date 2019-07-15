/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.ZeebeAssertHelper;
import io.zeebe.client.api.response.BrokerInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TaskEventClusteredTest {
  public ClusteringRule clusteringRule = new ClusteringRule();
  public GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);

  @Test
  public void shouldCreateJobWhenFollowerUnavailable() {
    // given
    final BrokerInfo leader = clusteringRule.getLeaderForPartition(START_PARTITION_ID);

    // choosing a new leader in a raft group where the previously leading broker is no longer
    // available
    clusteringRule.stopBroker(leader.getNodeId());

    // when
    clientRule.createSingleJob("bar");

    // then
    ZeebeAssertHelper.assertJobCreated("bar");
  }
}
