/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static io.camunda.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.AtomixCluster;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.test.TestActorSchedulerFactory;
import io.camunda.zeebe.broker.test.TestBrokerClientFactory;
import io.camunda.zeebe.broker.test.TestClusterFactory;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class SimpleBrokerStartTest {

  private static final SpringBrokerBridge TEST_SPRING_BROKER_BRIDGE = new SpringBrokerBridge();

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private File newTemporaryFolder;
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Before
  public void setup() throws Exception {
    newTemporaryFolder = temporaryFolder.newFolder();
  }

  @Test
  public void shouldFailToCreateBrokerWithSmallSnapshotPeriod() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().setSnapshotPeriod(Duration.ofMillis(1));
    brokerCfg.init(newTemporaryFolder.getAbsolutePath());

    // when

    final var catchedThrownBy =
        assertThatThrownBy(
            () -> {
              final var systemContext =
                  new SystemContext(
                      brokerCfg,
                      mock(ActorScheduler.class),
                      mock(AtomixCluster.class),
                      mock(BrokerClient.class),
                      new SecurityConfiguration(),
                      mock(UserServices.class),
                      mock(PasswordEncoder.class),
                      mock(JwtDecoder.class));
              new Broker(systemContext, TEST_SPRING_BROKER_BRIDGE, emptyList());
            });

    // then
    catchedThrownBy.isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldCallPartitionListenerAfterStart() throws Exception {
    // given
    final var brokerCfg = new BrokerCfg();
    assignSocketAddresses(brokerCfg);
    brokerCfg.init(newTemporaryFolder.getAbsolutePath());

    final var atomixCluster = TestClusterFactory.createAtomixCluster(brokerCfg, meterRegistry);
    final var actorScheduler = TestActorSchedulerFactory.ofBrokerConfig(brokerCfg);
    final var brokerClient =
        TestBrokerClientFactory.createBrokerClient(atomixCluster, actorScheduler);

    final var systemContext =
        new SystemContext(
            brokerCfg,
            actorScheduler,
            atomixCluster,
            brokerClient,
            new SecurityConfiguration(),
            mock(UserServices.class),
            mock(PasswordEncoder.class),
            mock(JwtDecoder.class));

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
