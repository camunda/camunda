/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.startup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.it.cluster.clustering.ClusteringRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class BrokerRestartTest {
  private final ClusteringRule clusteringRule = new ClusteringRule(1, 1, 1);
  private final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);

  @Test
  public void shouldSortRecordsByPosition() {
    // given
    // when
    generateLoad();
    restartBroker();
    generateLoad();
    restartBroker();
    generateLoad();

    // then
    final var log = clusteringRule.getLogStream(1);
    final var reader = log.newLogStreamReader();
    reader.seekToFirstEvent();
    assertThat(reader.hasNext()).isTrue();

    var previousPosition = -1L;
    while (reader.hasNext()) {
      final var position = reader.next().getPosition();
      assertThat(position).isGreaterThan(previousPosition);
      previousPosition = position;
    }
  }

  private void restartBroker() {
    clusteringRule.stopBroker(0);
    clusteringRule.startBroker(0);
  }

  private void generateLoad() {
    publishMessage(1).join();
    publishMessage(2).join();
  }

  private CamundaFuture<PublishMessageResponse> publishMessage(final int key) {
    return clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey(String.valueOf(key))
        .timeToLive(Duration.ofMinutes(2))
        .send();
  }
}
