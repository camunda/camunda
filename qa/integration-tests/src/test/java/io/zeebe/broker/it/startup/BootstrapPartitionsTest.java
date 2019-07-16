/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.startup;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.clustering.ClusteringRule;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BootstrapPartitionsTest {

  private final Timeout testTimeout = Timeout.seconds(30);
  private final ClusteringRule clusteringRule = new ClusteringRule(2, 2, 2);
  private final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldCreateDefaultPartitionsOnBootstrappedBrokers() {
    // when
    final List<Integer> partitions = clientRule.getPartitions();

    // then
    assertThat(partitions.size()).isEqualTo(2);
    assertThat(partitions).containsExactlyInAnyOrder(START_PARTITION_ID, START_PARTITION_ID + 1);
  }
}
