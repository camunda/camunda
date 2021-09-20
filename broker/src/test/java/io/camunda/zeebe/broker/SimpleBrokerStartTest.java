/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import static io.camunda.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SimpleBrokerStartTest {

  private static final SpringBrokerBridge TEST_SPRING_BROKER_BRIDGE = new SpringBrokerBridge();

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private File newTemporaryFolder;

  @Before
  public void setup() throws Exception {
    newTemporaryFolder = temporaryFolder.newFolder();
  }

  @Test
  public void shouldFailToCreateBrokerWithSmallSnapshotPeriod() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMillis(1));

    // when

    final var catchedThrownBy =
        assertThatThrownBy(
            () -> {
              final var systemContext =
                  new SystemContext(brokerCfg, newTemporaryFolder.getAbsolutePath(), null);
              new Broker(systemContext, TEST_SPRING_BROKER_BRIDGE);
            });

    // then
    catchedThrownBy.isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldCallPartitionListenerAfterStart() throws Exception {
    // given
    final var brokerCfg = new BrokerCfg();
    assignSocketAddresses(brokerCfg);
    final var systemContext =
        new SystemContext(brokerCfg, newTemporaryFolder.getAbsolutePath(), null);
    systemContext.getScheduler().start();

    final var leaderLatch = new CountDownLatch(1);
    final var listener =
        new PartitionListener() {
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
            leaderLatch.countDown();
            return CompletableActorFuture.completed(null);
          }

          @Override
          public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
            return CompletableActorFuture.completed(null);
          }
        };
    final var broker =
        new Broker(systemContext, TEST_SPRING_BROKER_BRIDGE, Collections.singletonList(listener));

    // when
    broker.start().join();

    // then
    leaderLatch.await();
    broker.close();
    systemContext.getScheduler().stop().get();
  }
}
