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
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.partition.RaftPartition;
import io.zeebe.broker.Broker;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class HealthMonitoringTest {

  private final EmbeddedBrokerRule embeddedBrokerRule = new EmbeddedBrokerRule();
  private final Timeout timeout = Timeout.seconds(120);

  @Rule public final RuleChain chain = RuleChain.outerRule(timeout).around(embeddedBrokerRule);

  @Test
  public void shouldReportUnhealthyWhenRaftInactive() {
    // given
    final Broker leader = embeddedBrokerRule.getBroker();
    assertThat(isBrokerHealthy()).isTrue();

    // when
    final var raftPartition =
        (RaftPartition)
            leader
                .getAtomix()
                .getPartitionService()
                .getPartitionGroup(GROUP_NAME)
                .getPartition(PartitionId.from(GROUP_NAME, START_PARTITION_ID));
    raftPartition.getServer().stop();

    // then
    Awaitility.waitAtMost(Duration.ofMinutes(1)).until(() -> !isBrokerHealthy());
  }

  private boolean isBrokerHealthy() {
    return embeddedBrokerRule
        .getSpringBrokerBridge()
        .getBrokerHealthCheckService()
        .get()
        .isBrokerHealthy();
  }
}
