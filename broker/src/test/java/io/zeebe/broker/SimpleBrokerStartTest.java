/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import static io.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SimpleBrokerStartTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private File newTemporaryFolder;

  @Before
  public void setup() throws Exception {
    newTemporaryFolder = temporaryFolder.newFolder();
  }

  @Test
  public void shouldFailToStartBrokerWithSmallTimeout() {
    // given
    final var brokerCfg = new BrokerCfg();
    assignSocketAddresses(brokerCfg);
    brokerCfg.setStepTimeout(Duration.ofMillis(1));

    final var broker = new Broker(brokerCfg, newTemporaryFolder.getAbsolutePath(), null);

    // when
    final var catchedThrownBy = assertThatThrownBy(() -> broker.start().join());

    // then
    catchedThrownBy.hasRootCauseInstanceOf(TimeoutException.class);
  }

  @Test
  public void shouldFailToCreateBrokerWithSmallSnapshotPeriod() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMillis(1));

    // when
    final var catchedThrownBy =
        assertThatThrownBy(() -> new Broker(brokerCfg, newTemporaryFolder.getAbsolutePath(), null));

    // then
    catchedThrownBy.isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldCallPartitionListenerAfterStart() throws Exception {
    // given
    final var brokerCfg = new BrokerCfg();
    assignSocketAddresses(brokerCfg);
    final var broker = new Broker(brokerCfg, newTemporaryFolder.getAbsolutePath(), null);

    final var leaderLatch = new CountDownLatch(1);
    broker.addPartitionListener(
        new PartitionListener() {
          @Override
          public ActorFuture<Void> onBecomingFollower(
              final int partitionId, final long term, final LogStream logStream) {
            return CompletableActorFuture.completed(null);
          }

          @Override
          public ActorFuture<Void> onBecomingLeader(
              final int partitionId, final long term, final LogStream logStream) {
            leaderLatch.countDown();
            return CompletableActorFuture.completed(null);
          }
        });

    // when
    broker.start().join();

    // then
    leaderLatch.await();
    broker.close();
  }
}
