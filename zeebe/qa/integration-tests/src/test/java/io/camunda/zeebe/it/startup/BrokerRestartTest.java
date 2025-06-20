/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.startup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.unifiedconfig.UnifiedConfiguration;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class BrokerRestartTest {
  private final UnifiedConfiguration unifiedConfiguration = new UnifiedConfiguration();
  private final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(unifiedConfiguration);
  private final GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Test
  public void shouldSortRecordsByPosition() {
    // given
    final var listener = new Listener();

    // when
    generateLoad();
    brokerRule.restartBroker(listener);
    generateLoad();
    brokerRule.restartBroker(listener);
    generateLoad();

    // then
    final var log = listener.get();
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

  private static final class Listener implements PartitionListener {
    private volatile LogStream logStream;

    private LogStream get() {
      return logStream;
    }

    @Override
    public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> onBecomingLeader(
        final int partitionId,
        final long term,
        final LogStream logStream,
        final QueryService queryService) {
      this.logStream = logStream;
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
      return CompletableActorFuture.completed(null);
    }
  }
}
