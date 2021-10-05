/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.health;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class HealthMonitoringTest {

  private final EmbeddedBrokerRule embeddedBrokerRule = new EmbeddedBrokerRule();
  private final Timeout timeout = Timeout.seconds(5 * 60);

  @Rule public final RuleChain chain = RuleChain.outerRule(timeout).around(embeddedBrokerRule);

  @Test
  public void shouldReportUnhealthyWhenRaftInactive() {
    // given
    final Broker leader = embeddedBrokerRule.getBroker();
    /* timeouts are selected generously as at the time of this implementation there is a
     * 1 minute cycle to update the state
     */
    await("Broker is healthy")
        .atMost(Duration.ofMinutes(2))
        .until(
            () -> {
              embeddedBrokerRule.getClock().addTime(Duration.ofMinutes(1));
              return isBrokerHealthy();
            });

    // when
    final var raftPartition =
        (RaftPartition)
            leader
                .getBrokerContext()
                .getPartitionManager()
                .getPartitionGroup()
                .getPartition(
                    PartitionId.from(PartitionManagerImpl.GROUP_NAME, START_PARTITION_ID));
    raftPartition.getServer().stop();

    // then
    /* timeouts are selected generously as at the time of this implementation there is a
     * 1 minute cycle to update the state
     */
    waitAtMost(Duration.ofMinutes(2))
        .until(
            () -> {
              embeddedBrokerRule.getClock().addTime(Duration.ofMinutes(1));
              return !isBrokerHealthy();
            });
  }

  private boolean isBrokerHealthy() {
    return embeddedBrokerRule
        .getSpringBrokerBridge()
        .getBrokerHealthCheckService()
        .get()
        .isBrokerHealthy();
  }
}
