/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.startup;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.response.PublishMessageResponse;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class BrokerRestartTest {
  private final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  private final GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Test
  public void shouldSortRecordsByPosition() {
    // given
    final var listener = new Listener();
    brokerRule.getBroker().addPartitionListener(listener);

    // when
    generateLoad();
    brokerRule.restartBroker(listener);
    generateLoad();
    brokerRule.restartBroker(listener);
    generateLoad();

    // then
    final var log = listener.get();
    final var reader = log.newLogStreamReader().join();
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

  private ZeebeFuture<PublishMessageResponse> publishMessage(final int key) {
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
        final int partitionId, final long term, final LogStream logStream) {
      this.logStream = logStream;
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
      return CompletableActorFuture.completed(null);
    }
  }
}
