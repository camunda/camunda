/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.system.management.BrokerAdminService;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.engine.processing.streamprocessor.StreamProcessor.Phase;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerAdminServiceTest {
  private final Timeout testTimeout = Timeout.seconds(60);
  private final EmbeddedBrokerRule embeddedBrokerRule =
      new EmbeddedBrokerRule(
          cfg -> {
            cfg.getData().setLogIndexDensity(1);
            cfg.getCluster().setPartitionsCount(2);
          });
  private final GrpcClientRule clientRule = new GrpcClientRule(embeddedBrokerRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(embeddedBrokerRule).around(clientRule);

  private BrokerAdminService brokerAdminService;

  @Before
  public void before() {
    final var broker = embeddedBrokerRule.getBroker();
    brokerAdminService = broker.getBrokerAdminService();
  }

  @Test
  public void shouldTakeSnapshotWhenRequested() {
    // given
    clientRule.createSingleJob("test");

    // when
    brokerAdminService.takeSnapshot();

    // then
    waitForSnapshotAtBroker(brokerAdminService);
  }

  @Test
  public void shouldPauseStreamProcessorWhenRequested() {
    // given
    clientRule.createSingleJob("test");

    // when
    brokerAdminService.pauseStreamProcessing();

    // then
    assertStreamProcessorPhase(brokerAdminService, Phase.PAUSED);
  }

  @Test
  public void shouldUnPauseStreamProcessorWhenRequested() {
    // given
    clientRule.createSingleJob("test");

    // when
    brokerAdminService.pauseStreamProcessing();
    assertStreamProcessorPhase(brokerAdminService, Phase.PAUSED);
    brokerAdminService.resumeStreamProcessing();

    // then
    assertStreamProcessorPhase(brokerAdminService, Phase.PROCESSING);
  }

  @Test
  public void shouldPauseStreamProcessorAndTakeSnapshotWhenPrepareUgrade() {
    // given
    clientRule.createSingleJob("test");

    // when
    brokerAdminService.prepareForUpgrade();

    // then
    waitForSnapshotAtBroker(brokerAdminService);

    assertStreamProcessorPhase(brokerAdminService, Phase.PAUSED);
    assertProcessedPositionIsInSnapshot(brokerAdminService);
  }

  private void assertStreamProcessorPhase(
      final BrokerAdminService brokerAdminService, final Phase expected) {
    Awaitility.await()
        .untilAsserted(
            () ->
                brokerAdminService
                    .getPartitionStatus()
                    .forEach(
                        (p, status) ->
                            assertThat(status.getStreamProcessorPhase()).isEqualTo(expected)));
  }

  private void assertProcessedPositionIsInSnapshot(final BrokerAdminService brokerAdminService) {
    Awaitility.await()
        .untilAsserted(
            () ->
                brokerAdminService
                    .getPartitionStatus()
                    .forEach(
                        (p, status) ->
                            assertThat(status.getProcessedPosition())
                                .isEqualTo(status.getProcessedPositionInSnapshot())));
  }

  private void waitForSnapshotAtBroker(final BrokerAdminService adminService) {
    Awaitility.await()
        .untilAsserted(
            () ->
                adminService
                    .getPartitionStatus()
                    .values()
                    .forEach(
                        status -> assertThat(status.getProcessedPositionInSnapshot()).isNotNull()));
  }
}
