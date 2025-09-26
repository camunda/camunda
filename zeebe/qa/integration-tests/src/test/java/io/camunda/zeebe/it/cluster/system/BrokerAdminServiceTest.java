/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.Future;
import org.agrona.DirectBuffer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class BrokerAdminServiceTest {
  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withBrokerConfig(cfg -> cfg.getData().setLogIndexDensity(1));

  @AutoClose private ZeebeResourcesHelper resourcesHelper;
  private PartitionsActuator partitions;

  @BeforeEach
  void beforeEach() {
    partitions = PartitionsActuator.of(zeebe);
    resourcesHelper = new ZeebeResourcesHelper(zeebe.newClientBuilder().build());
  }

  @Test
  void shouldTakeSnapshotWhenRequested() {
    // given
    resourcesHelper.createSingleJob("test");

    // when
    partitions.takeSnapshot();

    // then
    waitForSnapshotAtBroker();
  }

  @Test
  void shouldPauseStreamProcessorWhenRequested() {
    // given
    resourcesHelper.createSingleJob("test");

    // when
    final var status = partitions.pauseProcessing();

    // then
    assertThat(status.get(1).streamProcessorPhase()).isEqualTo(Phase.PAUSED.toString());
  }

  @Test
  void shouldResumeStreamProcessorWhenRequested() {
    // given
    partitions.pauseProcessing();

    // when
    final var status = partitions.resumeProcessing();

    // then
    try (final var client = zeebe.newClientBuilder().build()) {
      final Future<?> response =
          client.newPublishMessageCommand().messageName("test2").correlationKey("test-key").send();

      assertThat(response).isNotNull().succeedsWithin(Duration.ofSeconds(5));
    }

    assertThat(status.get(1).streamProcessorPhase()).isEqualTo(Phase.PROCESSING.toString());
  }

  @Test
  void shouldReturnProcessingPausedInsteadOfMessageTimeout() {
    // given
    partitions.pauseProcessing();

    // when
    final Future<?> response;
    try (final var client = zeebe.newClientBuilder().preferRestOverGrpc(true).build()) {
      response =
          client.newPublishMessageCommand().messageName("test").correlationKey("test-key").send();

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .havingCause()
          .withMessageContaining("Processing paused for partition")
          .asInstanceOf(InstanceOfAssertFactories.throwable(ProblemException.class))
          .extracting(ProblemException::code)
          .isEqualTo(503);
    }
  }

  @Test
  void shouldPauseExporterWhenRequested() {
    // when
    final var status = partitions.pauseExporting();

    // then
    assertThat(status.get(1).exporterPhase()).isEqualTo(ExporterPhase.PAUSED.toString());
  }

  @Test
  void shouldSoftPauseExporterWhenRequested() {
    // when
    final var status = partitions.softPauseExporting();

    // then
    assertThat(status.get(1).exporterPhase()).isEqualTo(ExporterPhase.SOFT_PAUSED.toString());
  }

  @Test
  void shouldContinueToExportWhileSoftPaused() {
    // given
    partitions.softPauseExporting();

    // when
    try (final var client = zeebe.newClientBuilder().build()) {
      client
          .newPublishMessageCommand()
          .messageName("test")
          .correlationKey("test-key")
          .send()
          .join();
    }

    // then
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
                    .withName("test")
                    .exists());
  }

  @Test
  void shouldResumeExportingFromSoftPausedWhenRequested() {
    // given
    partitions.softPauseExporting();

    // when
    final var status = partitions.resumeExporting();

    // then
    assertThat(status.get(1).exporterPhase()).isEqualTo(ExporterPhase.EXPORTING.toString());
  }

  @Test
  void shouldResumeExportingWhenRequested() {
    // given
    partitions.pauseExporting();

    // when
    try (final var client = zeebe.newClientBuilder().build()) {
      client
          .newPublishMessageCommand()
          .messageName("test")
          .correlationKey("test-key")
          .send()
          .join();
    }
    final var status = partitions.resumeExporting();

    // then
    assertThat(status.get(1).exporterPhase()).isEqualTo(ExporterPhase.EXPORTING.toString());
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
                    .withName("test")
                    .exists());
  }

  @Test
  void shouldPauseStreamProcessorAndExporterAndTakeSnapshotWhenPrepareUgrade() {
    // given
    resourcesHelper.createSingleJob("test");

    // when
    partitions.prepareUpgrade();
    waitForSnapshotAtBroker();

    // then
    final var status = partitions.query();
    assertThat(status.get(1).streamProcessorPhase()).isEqualTo(Phase.PAUSED.toString());
    assertThat(status.get(1).exporterPhase()).isEqualTo(ExporterPhase.PAUSED.toString());
    assertThat(status.get(1).processedPosition())
        .isEqualTo(status.get(1).processedPositionInSnapshot());
  }

  @Test
  void shouldPauseStreamProcessorAfterRestart() {
    // given
    partitions.pauseProcessing();

    // when
    zeebe.stop().start().awaitCompleteTopology();

    // then
    assertThat(partitions.query().get(1).streamProcessorPhase()).isEqualTo(Phase.PAUSED.toString());
  }

  @Test
  void shouldResumeStreamProcessorAfterRestart() {
    // given
    partitions.pauseProcessing();
    partitions.resumeProcessing();

    // when
    zeebe.stop().start().awaitCompleteTopology();

    // then
    assertThat(partitions.query().get(1).streamProcessorPhase())
        .isEqualTo(Phase.PROCESSING.toString());
  }

  @Test
  void shouldPauseExporterAfterRestart() {
    // given
    partitions.pauseExporting();

    // when
    zeebe.stop().start().awaitCompleteTopology();

    // then
    assertThat(partitions.query().get(1).exporterPhase())
        .isEqualTo(ExporterPhase.PAUSED.toString());
  }

  @Test
  void shouldResumeExporterAfterRestart() {
    // given
    partitions.pauseExporting();
    partitions.resumeExporting();

    // when
    zeebe.stop().start().awaitCompleteTopology();

    // then
    assertThat(partitions.query().get(1).exporterPhase())
        .isEqualTo(ExporterPhase.EXPORTING.toString());
  }

  @Test
  void shouldReturnStreamClock() {
    // given - evil way to modify the clock until we have a client API
    // TODO: replace this with an actual client command when we have the client API
    final var brokerClient = zeebe.bean(BrokerClient.class);
    final var expectedInstant = Instant.now().minusMillis(3600).truncatedTo(ChronoUnit.MILLIS);
    final var request =
        new TestClockRequest(new ClockRecord().pinAt(expectedInstant.toEpochMilli()));

    // when
    brokerClient.sendRequest(request, Duration.ofSeconds(30)).join();

    // then
    final var status = partitions.query();
    final var clock = status.get(1).clock();
    assertThat(clock.instant()).isEqualTo(expectedInstant);
    assertThat(clock.modificationType()).isEqualTo("Pin");
    assertThat(clock.modification()).containsEntry("at", expectedInstant.toString());
  }

  private void waitForSnapshotAtBroker() {
    Awaitility.await("snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .until(() -> partitions.query().get(1).snapshotId(), Objects::nonNull);
  }

  private static final class TestClockRequest extends BrokerExecuteCommand<ClockRecord> {
    private final ClockRecord request;

    public TestClockRequest(final ClockRecord request) {
      super(ValueType.CLOCK, ClockIntent.PIN);
      this.request = request;
    }

    @Override
    public BufferWriter getRequestWriter() {
      return request;
    }

    @Override
    protected ClockRecord toResponseDto(final DirectBuffer buffer) {
      final var response = new ClockRecord();
      response.wrap(buffer);
      return response;
    }
  }
}
