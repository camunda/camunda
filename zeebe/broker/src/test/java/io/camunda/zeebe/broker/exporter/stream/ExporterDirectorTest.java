/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static io.camunda.zeebe.test.util.TestUtil.doRepeatedly;
import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.broker.exporter.util.PojoConfigurationExporter;
import io.camunda.zeebe.broker.exporter.util.PojoConfigurationExporter.PojoExporterConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.impl.SkipPositionsFilter;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.verification.VerificationWithTimeout;

public final class ExporterDirectorTest {

  private static final String EXPORTER_ID_1 = "exporter-1";
  private static final String EXPORTER_ID_2 = "exporter-2";

  private static final int TIMEOUT_MILLIS = 5_000;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  @Rule public final ExporterRule rule = ExporterRule.activeExporter();
  @Rule public final ExporterRule passiveExporterRule = ExporterRule.passiveExporter();

  private final List<ControlledTestExporter> exporters = new ArrayList<>();
  private final List<ExporterDescriptor> exporterDescriptors = new ArrayList<>();

  @Before
  public void init() {
    exporters.clear();
    exporterDescriptors.clear();

    createExporter(EXPORTER_ID_1, Collections.singletonMap("x", 1));
    createExporter(EXPORTER_ID_2, Collections.singletonMap("y", 2));
  }

  private void createExporter(final String exporterId, final Map<String, Object> arguments) {
    final ControlledTestExporter exporter = spy(new ControlledTestExporter());

    final ExporterDescriptor descriptor =
        spy(new ExporterDescriptor(exporterId, exporter.getClass(), arguments));
    doAnswer(c -> exporter).when(descriptor).newInstance();

    exporters.add(exporter);
    exporterDescriptors.add(descriptor);
  }

  private void startExporterDirector(final List<ExporterDescriptor> exporterDescriptors) {
    rule.startExporterDirector(exporterDescriptors);
  }

  @Test
  public void shouldUpdatePositionWhenInitialRecordsAreSkipped() {
    // given
    final ControlledTestExporter tailingExporter = exporters.get(1);
    exporters.forEach(
        e ->
            e.onConfigure(withFilter(List.of(RecordType.COMMAND), List.of(ValueType.DEPLOYMENT)))
                .shouldAutoUpdatePosition(false));

    // when
    startExporterDirector(exporterDescriptors);
    final ExportersState state = rule.getExportersState();
    final long skippedRecordPosition =
        rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    rule.writeCommand(DeploymentIntent.CREATE, new DeploymentRecord());

    // then
    Awaitility.await("director has read all records until now")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(tailingExporter.getExportedRecords()).hasSize(1));
    assertThat(state.getPosition(EXPORTER_ID_1)).isEqualTo(skippedRecordPosition);
    assertThat(state.getPosition(EXPORTER_ID_2)).isEqualTo(skippedRecordPosition);
  }

  @Test
  public void shouldUpdatePositionOfUpToDateExportersOnSkipRecord() {
    // given
    final ControlledTestExporter filteringExporter = exporters.get(0);
    final ControlledTestExporter tailingExporter = exporters.get(1);
    tailingExporter
        .onConfigure(withFilter(List.of(RecordType.COMMAND), List.of(ValueType.DEPLOYMENT)))
        .shouldAutoUpdatePosition(false);
    filteringExporter
        .onConfigure(withFilter(List.of(RecordType.COMMAND), List.of(ValueType.DEPLOYMENT)))
        .shouldAutoUpdatePosition(false);

    // when
    startExporterDirector(exporterDescriptors);
    final ExportersState state = rule.getExportersState();

    // accepted by both
    final long firstRecordPosition =
        rule.writeCommand(DeploymentIntent.CREATE, new DeploymentRecord());
    Awaitility.await("filteringExporter has exported the first record")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(filteringExporter.getExportedRecords()).hasSize(1));
    filteringExporter.getController().updateLastExportedRecordPosition(firstRecordPosition);
    // skipped entirely
    final long skippedRecordPosition =
        rule.writeCommand(IncidentIntent.CREATED, new IncidentRecord());
    // accepted by both again
    rule.writeCommand(DeploymentIntent.CREATE, new DeploymentRecord());

    // then
    Awaitility.await("director has read all records until now")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(tailingExporter.getExportedRecords()).hasSize(2));
    assertThat(state.getPosition(EXPORTER_ID_1)).isEqualTo(skippedRecordPosition);
    assertThat(state.getPosition(EXPORTER_ID_2)).isEqualTo(-1L);
  }

  @Test
  public void shouldUpdateIfSkippingInitialRecordForSingleExporter() {
    final ControlledTestExporter filteringExporter = exporters.get(0);
    final ControlledTestExporter tailingExporter = exporters.get(1);
    tailingExporter
        .onConfigure(
            withFilter(
                List.of(RecordType.COMMAND, RecordType.EVENT), List.of(ValueType.DEPLOYMENT)))
        .shouldAutoUpdatePosition(false);
    filteringExporter
        .onConfigure(withFilter(List.of(RecordType.COMMAND), List.of(ValueType.DEPLOYMENT)))
        .shouldAutoUpdatePosition(false);

    // when
    startExporterDirector(exporterDescriptors);
    final ExportersState state = rule.getExportersState();

    // skipped only by filteringExporter
    final long skippedRecordPosition =
        rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    // accepted by both
    rule.writeCommand(DeploymentIntent.CREATE, new DeploymentRecord());

    // then
    Awaitility.await("director has read all records until now")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(tailingExporter.getExportedRecords()).hasSize(2));
    assertThat(state.getPosition(EXPORTER_ID_1)).isEqualTo(skippedRecordPosition);
    assertThat(state.getPosition(EXPORTER_ID_2)).isEqualTo(-1L);
  }

  @Test
  public void shouldRetryOpenCallIfFails() throws Exception {
    // given, when
    final var exporter = startExporterWithFaultyOpenCall();

    // then
    Awaitility.await("exporter open has been retried")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> verify(exporter, times(2)).open(any()));

    rule.closeExporterDirector();
  }

  @Test
  public void shouldExportAfterOpenRetried() throws Exception {
    // given
    final var exporter = startExporterWithFaultyOpenCall();

    Awaitility.await("exporter open has been retried")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> verify(exporter, times(2)).open(any()));

    // when
    final var eventPosition1 = writeEvent();
    final var eventPosition2 = writeEvent();

    // then
    Awaitility.await("Exporter has exported all records")
        .untilAsserted(
            () ->
                assertThat(exporter.getExportedRecords())
                    .extracting(Record::getPosition)
                    .containsExactly(eventPosition1, eventPosition2));

    rule.closeExporterDirector();
  }

  @Test
  public void shouldNotStartExportingUntilExportersFinishOpening() throws Exception {
    writeEvent();

    final var exporter = startExporterWithFaultyOpenCall();

    Awaitility.await("Record has been exported")
        .until(() -> !exporter.getExportedRecords().isEmpty());

    final InOrder inOrder = inOrder(exporter);

    inOrder.verify(exporter).open(any());
    inOrder.verify(exporter).open(any());
    inOrder.verify(exporter, timeout(5000)).export(any());

    rule.closeExporterDirector();
  }

  @Test
  public void shouldNotResumeExportingUntilExportersFinishOpening() throws Exception {
    // given
    final var exporter = startExporterWithFaultyOpenCall(null);
    writeEvent();

    Awaitility.await("exporter open has been retried")
        .untilAsserted(() -> verify(exporter, times(3)).open(any()));

    // when - Resume exporting while exporters are still trying to open
    rule.getDirector().resumeExporting().join();

    // then - no records have been exported
    assertThat(exporter.getExportedRecords()).isEmpty();
    verify(exporter, never()).export(any());

    // when - the exporter finally opens successfully
    doCallRealMethod().when(exporter).open(any());

    // then - the record is exported
    Awaitility.await("Record has been exported")
        .until(() -> !exporter.getExportedRecords().isEmpty());

    rule.closeExporterDirector();
  }

  @Test
  public void shouldUpdateIfRecordSkipsSingleUpToDateExporter() {
    final ControlledTestExporter filteringExporter = exporters.get(0);
    final ControlledTestExporter tailingExporter = exporters.get(1);
    tailingExporter
        .onConfigure(
            withFilter(
                List.of(RecordType.COMMAND, RecordType.EVENT), List.of(ValueType.DEPLOYMENT)))
        .shouldAutoUpdatePosition(false);
    filteringExporter
        .onConfigure(withFilter(List.of(RecordType.COMMAND), List.of(ValueType.DEPLOYMENT)))
        .shouldAutoUpdatePosition(false);

    // when
    startExporterDirector(exporterDescriptors);
    final ExportersState state = rule.getExportersState();

    // accepted by both
    final long firstRecordPosition =
        rule.writeCommand(DeploymentIntent.CREATE, new DeploymentRecord());
    Awaitility.await("filteringExporter has exported the first record")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(filteringExporter.getExportedRecords()).hasSize(1));
    filteringExporter.getController().updateLastExportedRecordPosition(firstRecordPosition);
    // skipped only by filteringExporter
    final long skippedRecordPosition =
        rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    // then
    Awaitility.await("director has read all records until now")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(tailingExporter.getExportedRecords()).hasSize(2));
    assertThat(state.getPosition(EXPORTER_ID_1)).isEqualTo(skippedRecordPosition);
    assertThat(state.getPosition(EXPORTER_ID_2)).isEqualTo(-1L);
  }

  @Test
  public void shouldConfigureAllExportersProperlyOnStart() throws InterruptedException {
    // when
    final CountDownLatch latch = new CountDownLatch(exporters.size());
    exporters.forEach(exporter -> exporter.onOpen(c -> latch.countDown()));
    startExporterDirector(exporterDescriptors);

    // then
    assertThat(latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
    verify(exporters.get(0), TIMEOUT).open(any());
    verify(exporters.get(1), TIMEOUT).open(any());

    exporters.forEach(
        exporter -> {
          assertThat(exporter.getController()).isNotNull();
          assertThat(exporter.getContext().getLogger()).isNotNull();
          assertThat(exporter.getContext().getConfiguration()).isNotNull();
        });

    final Context exporterContext1 = exporters.get(0).getContext();
    assertThat(exporterContext1.getConfiguration().getId()).isEqualTo(EXPORTER_ID_1);
    assertThat(exporterContext1.getConfiguration().getArguments())
        .isEqualTo(Collections.singletonMap("x", 1));
    assertThat(exporterContext1.getLogger().getName())
        .isEqualTo(Loggers.getExporterLogger(EXPORTER_ID_1).getName());

    final Context exporterContext2 = exporters.get(1).getContext();
    assertThat(exporterContext2.getConfiguration().getId()).isEqualTo(EXPORTER_ID_2);
    assertThat(exporterContext2.getConfiguration().getArguments())
        .isEqualTo(Collections.singletonMap("y", 2));
    assertThat(exporterContext2.getLogger().getName())
        .isEqualTo(Loggers.getExporterLogger(EXPORTER_ID_2).getName());
  }

  @Test
  public void shouldIgnoreErrorsOnClose() throws Exception {
    // given
    startExporterDirector(exporterDescriptors);

    // when -- closing the first exporter will throw an exception
    doThrow(new RuntimeException()).when(exporters.get(0)).close();
    rule.closeExporterDirector();

    // then -- we still call close for both exporters, ignoring the exception
    verify(exporters.get(0), TIMEOUT).close();
    verify(exporters.get(1), TIMEOUT).close();
  }

  @Test
  public void shouldCloseAllExportersOnClose() throws Exception {
    // given
    startExporterDirector(exporterDescriptors);

    // when
    rule.closeExporterDirector();

    // then
    verify(exporters.get(0), TIMEOUT).close();
    verify(exporters.get(1), TIMEOUT).close();
  }

  @Test
  public void shouldCloseAllExportersOnCloseInPassiveMode() throws Exception {
    // given
    passiveExporterRule.startExporterDirector(exporterDescriptors);

    // when
    passiveExporterRule.closeExporterDirector();

    // then
    verify(exporters.get(0), TIMEOUT).close();
    verify(exporters.get(1), TIMEOUT).close();
  }

  @Test
  public void shouldInstantiateConfigurationClass() {
    // given
    final String foo = "bar";
    final int x = 123;
    final String bar = "baz";
    final double y = 32.12;

    final Map<String, Object> nested = new HashMap<>();
    nested.put("bar", bar);
    nested.put("y", y);

    final Map<String, Object> arguments = new HashMap<>();
    arguments.put("foo", foo);
    arguments.put("x", x);
    arguments.put("nested", nested);

    final ExporterDescriptor descriptor =
        new ExporterDescriptor(
            "instantiateConfiguration", PojoConfigurationExporter.class, arguments);

    startExporterDirector(Collections.singletonList(descriptor));

    // then
    waitUntil(() -> PojoConfigurationExporter.configuration != null);
    final PojoExporterConfiguration configuration = PojoConfigurationExporter.configuration;

    assertThat(configuration.getFoo()).isEqualTo(foo);
    assertThat(configuration.getX()).isEqualTo(x);
    assertThat(configuration.getNested().getBar()).isEqualTo(bar);
    assertThat(configuration.getNested().getY()).isEqualTo(y);
  }

  @Test
  public void shouldApplyRecordFilter() {
    // given
    exporters
        .get(0)
        .onConfigure(
            withFilter(
                Arrays.asList(RecordType.COMMAND, RecordType.EVENT),
                Collections.singletonList(ValueType.DEPLOYMENT),
                Arrays.asList(DeploymentIntent.CREATED, DeploymentIntent.CREATE)));

    exporters
        .get(1)
        .onConfigure(
            withFilter(
                Collections.singletonList(RecordType.EVENT),
                Arrays.asList(ValueType.DEPLOYMENT, ValueType.JOB),
                Arrays.asList(
                    DeploymentIntent.CREATED, DeploymentIntent.CREATE, JobIntent.CREATED)));

    startExporterDirector(exporterDescriptors);

    // when
    final long deploymentCommand =
        rule.writeCommand(DeploymentIntent.CREATE, new DeploymentRecord());
    final long deploymentEvent = rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    rule.writeEvent(IncidentIntent.CREATED, new IncidentRecord());
    final long jobEvent = rule.writeEvent(JobIntent.CREATED, new JobRecord());

    // then
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 2);

    assertThat(exporters.get(0).getExportedRecords())
        .extracting(Record::getPosition)
        .hasSize(2)
        .contains(deploymentCommand, deploymentEvent);
    assertThat(exporters.get(1).getExportedRecords())
        .extracting(Record::getPosition)
        .hasSize(2)
        .contains(deploymentEvent, jobEvent);
  }

  @Test
  public void shouldNotExportSkipRecordsFilter() {
    // given
    exporters
        .get(1)
        .onConfigure(withFilter(List.of(RecordType.COMMAND), List.of(ValueType.DEPLOYMENT)));

    rule.withPositionsToSkipFilter(SkipPositionsFilter.of(Set.of(1L)));
    startExporterDirector(exporterDescriptors);

    // when
    rule.writeCommand(DeploymentIntent.CREATE, new DeploymentRecord());
    rule.writeCommand(DeploymentIntent.CREATE, new DeploymentRecord());

    // then
    Awaitility.await("filteringExporter has exported only the second record")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(exporters.get(1).getExportedRecords())
                    .extracting(Record::getPosition)
                    .containsExactly(2L));
  }

  @Test
  public void shouldRetryExportingOnException() {
    // given
    final AtomicLong failCount = new AtomicLong(3);
    exporters
        .get(0)
        .onExport(
            e -> {
              if (failCount.getAndDecrement() > 0) {
                throw new RuntimeException("Export failed (expected)");
              }
            });

    startExporterDirector(exporterDescriptors);

    // when
    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();

    // then
    doRepeatedly(() -> rule.getClock().addTime(Duration.ofSeconds(1)))
        .until((r) -> failCount.get() <= -2);

    Awaitility.await("Exporter %s has exported all records".formatted(EXPORTER_ID_1))
        .untilAsserted(
            () ->
                assertThat(exporters.get(0).getExportedRecords())
                    .extracting(Record::getPosition)
                    .containsExactly(eventPosition1, eventPosition2));
    Awaitility.await("Exporter %s has exported all records".formatted(EXPORTER_ID_2))
        .untilAsserted(
            () ->
                assertThat(exporters.get(1).getExportedRecords())
                    .extracting(Record::getPosition)
                    .containsExactly(eventPosition1, eventPosition2));
  }

  @Test
  public void shouldNotRetryExportingOnDeserializationException() {
    // given
    final var recordExporterRef = new AtomicReference<RecordExporter>();
    rule.startExporterDirector(
        exporterDescriptors,
        ExporterPhase.EXPORTING,
        recordExporter -> failingRecordExporter(recordExporter, recordExporterRef));
    Awaitility.await("exporter director has started")
        .untilAsserted(() -> assertThat(recordExporterRef.get()).isNotNull());
    final var recordExporter = recordExporterRef.get();
    doThrow(new RuntimeException("deserialization exception")).when(recordExporter).wrap(any());

    // when
    final long eventPosition1 = writeEvent();

    // then
    verify(recordExporter, timeout(10000))
        .wrap(argThat(evt -> evt.getPosition() == eventPosition1));
    verifyNoMoreInteractions(recordExporter);
    Awaitility.await("Until director is unhealthy")
        .untilAsserted(
            () ->
                assertThat(rule.getDirector().getHealthReport().status())
                    .isEqualTo(HealthStatus.DEAD));
    assertThat(rule.getDirector().getPhase())
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(phase -> assertThat(phase).isEqualTo(ExporterPhase.CLOSED));
  }

  @Test
  public void shouldExecuteScheduledTask() throws Exception {
    // given
    final CountDownLatch timerTriggerLatch = new CountDownLatch(1);
    final CountDownLatch timerScheduledLatch = new CountDownLatch(1);
    final Duration delay = Duration.ofSeconds(10);

    exporters
        .get(0)
        .onExport(
            r -> {
              exporters
                  .get(0)
                  .getController()
                  .scheduleCancellableTask(delay, timerTriggerLatch::countDown);
              timerScheduledLatch.countDown();
            });

    // when
    startExporterDirector(exporterDescriptors);

    writeEvent();

    assertThat(timerScheduledLatch.await(5, TimeUnit.SECONDS)).isTrue();

    rule.getClock().addTime(delay);

    // then
    assertThat(timerTriggerLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldExecuteScheduledCancellableTask() throws InterruptedException {
    // given
    final CountDownLatch timerTriggerLatch = new CountDownLatch(1);
    final CountDownLatch timerScheduledLatch = new CountDownLatch(1);
    final Duration delay = Duration.ofSeconds(10);

    final ControlledTestExporter exporter = exporters.get(0);
    exporter.onExport(
        r -> {
          exporter.getController().scheduleCancellableTask(delay, timerTriggerLatch::countDown);
          timerScheduledLatch.countDown();
        });

    // when
    startExporterDirector(exporterDescriptors);

    writeEvent();

    assertThat(timerScheduledLatch.await(5, TimeUnit.SECONDS)).isTrue();

    rule.getClock().addTime(delay);

    // then
    assertThat(timerTriggerLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldCancelScheduledCancellableTask() throws InterruptedException {
    // given
    final CountDownLatch timerScheduledLatch = new CountDownLatch(1);
    final CountDownLatch timerTriggerLatch = new CountDownLatch(1);
    final Duration delay = Duration.ofSeconds(10);

    final var exporter = exporters.get(0);
    final var shouldBeNotModifiedVariable = new AtomicLong(0);
    exporter.onExport(
        r -> {
          final var taskToCancel =
              exporter
                  .getController()
                  .scheduleCancellableTask(delay, () -> shouldBeNotModifiedVariable.set(1));
          exporter.getController().scheduleCancellableTask(delay, timerTriggerLatch::countDown);
          taskToCancel.cancel();
          timerScheduledLatch.countDown();
        });

    // when
    startExporterDirector(exporterDescriptors);

    writeEvent();

    assertThat(timerScheduledLatch.await(5, TimeUnit.SECONDS)).isTrue();

    rule.getClock().addTime(delay);

    // then
    assertThat(timerTriggerLatch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(shouldBeNotModifiedVariable.get()).isZero();
  }

  @Test
  public void shouldRecoverPositionsFromState() throws Exception {
    // given
    startExporterDirector(exporterDescriptors);

    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();

    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 2);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 2);

    exporters.get(0).getController().updateLastExportedRecordPosition(eventPosition2);
    exporters.get(1).getController().updateLastExportedRecordPosition(eventPosition1);

    rule.closeExporterDirector();
    exporters.get(0).getExportedRecords().clear();
    exporters.get(1).getExportedRecords().clear();

    // then
    startExporterDirector(exporterDescriptors);

    // then
    waitUntil(() -> exporters.get(1).getExportedRecords().size() >= 1);
    assertThat(exporters.get(0).getExportedRecords()).isEmpty();
    assertThat(exporters.get(1).getExportedRecords())
        .extracting(Record::getPosition)
        .hasSize(1)
        .contains(eventPosition2);
  }

  @Test
  public void shouldRecoverMetadataFromState() throws Exception {
    // given
    startExporterDirector(exporterDescriptors);

    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();
    final var exporterMetadata1 = "e1".getBytes();
    final var exporterMetadata2 = "e2".getBytes();

    Awaitility.await("wait until the exporters read the records")
        .until(
            () ->
                exporters.get(0).getExportedRecords().size() == 2
                    && exporters.get(1).getExportedRecords().size() == 2);

    exporters
        .get(0)
        .getController()
        .updateLastExportedRecordPosition(eventPosition2, exporterMetadata1);
    exporters
        .get(1)
        .getController()
        .updateLastExportedRecordPosition(eventPosition1, exporterMetadata2);

    rule.closeExporterDirector();
    exporters.get(0).getExportedRecords().clear();
    exporters.get(1).getExportedRecords().clear();

    // then
    startExporterDirector(exporterDescriptors);
    Awaitility.await("wait until the exporters are opened")
        .until(() -> exporters.get(1).getExportedRecords().size() >= 1);

    // then
    assertThat(exporters.get(0).getController().readMetadata()).hasValue(exporterMetadata1);
    assertThat(exporters.get(1).getController().readMetadata()).hasValue(exporterMetadata2);
  }

  @Test
  public void shouldNotUpdatePositionToSmallerValue() throws Exception {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    final var controlledTestExporter = exporters.get(0);
    controlledTestExporter.onOpen(c -> latch.countDown());
    startExporterDirector(exporterDescriptors);
    latch.await();
    exporters.get(0).getController().updateLastExportedRecordPosition(1);
    final var firstPosition =
        Awaitility.await()
            .until(() -> rule.getExportersState().getPosition("exporter-1"), (pos) -> pos > -1);

    // when
    exporters.get(0).getController().updateLastExportedRecordPosition(-1);

    // then
    final var secondPosition = rule.getExportersState().getPosition("exporter-1");
    assertThat(secondPosition).isEqualTo(firstPosition);
  }

  @Test
  public void shouldUpdateLastExportedPositionOnClose() throws Exception {
    // given
    startExporterDirector(exporterDescriptors);

    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();

    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 2);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 2);

    exporters
        .get(0)
        .onClose(
            () ->
                exporters.get(0).getController().updateLastExportedRecordPosition(eventPosition1));

    // when
    rule.closeExporterDirector();
    exporters.get(0).getExportedRecords().clear();
    exporters.get(1).getExportedRecords().clear();

    startExporterDirector(exporterDescriptors);

    // then
    waitUntil(() -> exporters.get(1).getExportedRecords().size() >= 2);
    assertThat(exporters.get(0).getExportedRecords())
        .extracting(Record::getPosition)
        .hasSize(1)
        .contains(eventPosition2);
    assertThat(exporters.get(1).getExportedRecords())
        .extracting(Record::getPosition)
        .hasSize(2)
        .contains(eventPosition1, eventPosition2);
  }

  @Test
  public void shouldRemoveExporterFromState() throws Exception {
    // given
    startExporterDirector(exporterDescriptors);

    final long eventPosition = writeEvent();
    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 1);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 1);

    exporters.get(0).getController().updateLastExportedRecordPosition(eventPosition);
    exporters.get(1).getController().updateLastExportedRecordPosition(eventPosition);

    rule.closeExporterDirector();

    // when
    startExporterDirector(Collections.singletonList(exporterDescriptors.get(0)));

    verify(exporters.get(0), TIMEOUT.times(2)).open(any());

    // then
    final ExportersState exportersState = rule.getExportersState();
    assertThat(exportersState.getPosition(EXPORTER_ID_1)).isEqualTo(eventPosition);
    waitUntil(() -> exportersState.getPosition(EXPORTER_ID_2) == -1);
  }

  @Test
  public void shouldRecoverFromStartWithNonUpdatingExporter() throws Exception {
    // given
    startExporterDirector(exporterDescriptors);
    final long eventPosition = writeEvent();

    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 1);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 1);

    exporters.get(1).getController().updateLastExportedRecordPosition(eventPosition);

    // when
    rule.closeExporterDirector();
    exporters.get(0).getExportedRecords().clear();
    exporters.get(1).getExportedRecords().clear();
    startExporterDirector(exporterDescriptors);

    // then
    waitUntil(() -> exporters.get(0).getExportedRecords().size() >= 1);
    assertThat(exporters.get(0).getExportedRecords())
        .extracting(Record::getPosition)
        .containsExactly(eventPosition);
    assertThat(exporters.get(1).getExportedRecords()).isEmpty();
  }

  @Test
  public void shouldStartContainersSoftPaused() {
    // All containers of an exporter should be initialized as soft paused if this is also
    // the exporter director phase on initialization. This is to prevent that the
    // that exporter containers can start to update the exported position before the exporter
    // director is ready. When soft paused new events are exported the position should not update.

    // when
    rule.startExporterDirector(exporterDescriptors, ExporterPhase.SOFT_PAUSED);

    writeEvent();
    writeEvent();

    exporters.get(0).shouldAutoUpdatePosition(true);
    exporters.get(1).shouldAutoUpdatePosition(true);

    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 2);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 2);

    // then the position will not be updated.
    assertThat(rule.getExportersState().getPosition(EXPORTER_ID_1)).isEqualTo(-1);
    assertThat(rule.getExportersState().getPosition(EXPORTER_ID_2)).isEqualTo(-1);
  }

  private long writeEvent() {
    final DeploymentRecord event = new DeploymentRecord();
    return rule.writeEvent(DeploymentIntent.CREATED, event);
  }

  private Consumer<Context> withFilter(
      final List<RecordType> acceptedTypes, final List<ValueType> valueTypes) {
    return context ->
        context.setFilter(
            new Context.RecordFilter() {
              @Override
              public boolean acceptType(final RecordType recordType) {
                return acceptedTypes.contains(recordType);
              }

              @Override
              public boolean acceptValueType(final ValueType valueType) {
                return valueTypes.contains(valueType);
              }
            });
  }

  private Consumer<Context> withFilter(
      final List<RecordType> acceptedTypes,
      final List<ValueType> valueTypes,
      final List<Intent> intents) {
    return context ->
        context.setFilter(
            new Context.RecordFilter() {
              @Override
              public boolean acceptType(final RecordType recordType) {
                return acceptedTypes.contains(recordType);
              }

              @Override
              public boolean acceptValueType(final ValueType valueType) {
                return valueTypes.contains(valueType);
              }

              @Override
              public boolean acceptIntent(final Intent intent) {
                return intents.contains(intent);
              }
            });
  }

  private ControlledTestExporter startExporterWithFaultyOpenCall() {
    return startExporterWithFaultyOpenCall(1);
  }

  // if numberOfRetries is null, it will keep failing forever
  private ControlledTestExporter startExporterWithFaultyOpenCall(final Integer numberOfRetries) {
    final ControlledTestExporter exporter = spy(new ControlledTestExporter());

    var stubbing = doThrow(new RuntimeException("open failed"));
    if (numberOfRetries != null) {
      for (int i = 1; i < numberOfRetries; i++) {
        stubbing = stubbing.doThrow(new RuntimeException("open failed"));
      }
      stubbing = stubbing.doCallRealMethod();
    }
    stubbing.when(exporter).open(any());

    final ExporterDescriptor descriptor =
        spy(
            new ExporterDescriptor(
                "exporter-failing", exporter.getClass(), Collections.singletonMap("x", 1)));
    doAnswer(c -> exporter).when(descriptor).newInstance();

    startExporterDirector(List.of(descriptor));

    return exporter;
  }

  private RecordExporter failingRecordExporter(
      final RecordExporter recordExporter, final AtomicReference<RecordExporter> exporterRef) {
    return exporterRef.updateAndGet(ignored -> spy(recordExporter));
  }
}
