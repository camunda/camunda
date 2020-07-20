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
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.util.LangUtil;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class HealthMonitoringTest {
  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);
  private final Timeout testTimeout = Timeout.seconds(120);
  private final EmbeddedBrokerRule embeddedBrokerRule =
      new EmbeddedBrokerRule(
          cfg -> {
            cfg.getData().setSnapshotPeriod(SNAPSHOT_PERIOD);
            cfg.getData().setLogIndexDensity(1);
          });
  private final GrpcClientRule clientRule = new GrpcClientRule(embeddedBrokerRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(embeddedBrokerRule).around(clientRule);

  @Test
  public void shouldReportUnhealthyWhenRaftInactive() {
    // given
    final Broker leader = embeddedBrokerRule.getBroker();
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
}
