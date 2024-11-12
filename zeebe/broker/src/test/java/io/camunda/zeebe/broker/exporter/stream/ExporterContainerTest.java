/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterContainerTest {

  private static final String EXPORTER_ID = "fakeExporter";
  private static final int PARTITION_ID = 123;
  private static final String REGISTERED_COUNTER_NAME = "zeebe_exporter_counter";

  private ExporterContainerRuntime runtime;
  private FakeExporter exporter;
  private ExporterContainer exporterContainer;

  public static class FakeExporter implements Exporter {

    private Context context;
    private Controller controller;
    private Record<?> record;
    private boolean closed;

    public Context getContext() {
      return context;
    }

    public Controller getController() {
      return controller;
    }

    public Record<?> getRecord() {
      return record;
    }

    public boolean isClosed() {
      return closed;
    }

    @Override
    public void configure(final Context context) throws Exception {
      this.context = context;
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void close() {
      closed = true;
    }

    @Override
    public void export(final Record<?> record) {
      this.record = record;
    }
  }

  public static final class FakeExporterWithMetrics extends FakeExporter implements Exporter {
    @Override
    public void configure(final Context context) throws Exception {
      super.context = context;

      Counter.builder(REGISTERED_COUNTER_NAME).register(context.getMeterRegistry());
    }
  }

  private static final class AlwaysRejectingFilter implements Context.RecordFilter {

    @Override
    public boolean acceptType(final RecordType recordType) {
      return false;
    }

    @Override
    public boolean acceptValue(final ValueType valueType) {
      return false;
    }
  }

  @Nested
  class WithDefaultInitialization {

    @BeforeEach
    void beforeEach(final @TempDir Path storagePath) throws ExporterLoadException {
      runtime = new ExporterContainerRuntime(storagePath);

      final var descriptor =
          runtime
              .getRepository()
              .validateAndAddExporterDescriptor(
                  EXPORTER_ID, FakeExporter.class, Map.of("key", "value"));
      exporterContainer = runtime.newContainer(descriptor, PARTITION_ID);
      exporter = (FakeExporter) exporterContainer.getExporter();
    }

    @Test
    void shouldConfigureExporter() throws Exception {
      // given

      // when
      exporterContainer.configureExporter();

      // then
      assertThat(exporter.getContext()).isNotNull();
      assertThat(exporter.getContext().getLogger()).isNotNull();
      assertThat(exporter.getContext().getConfiguration()).isNotNull();
      assertThat(exporter.getContext().getConfiguration().getId()).isEqualTo(EXPORTER_ID);
      assertThat(exporter.getContext().getPartitionId()).isEqualTo(PARTITION_ID);
      assertThat(exporter.getContext().getConfiguration().getArguments())
          .isEqualTo(Map.of("key", "value"));
    }

    @Test
    void shouldOpenExporter() throws Exception {
      // given
      exporterContainer.configureExporter();

      // when
      exporterContainer.openExporter();

      // then
      assertThat(exporter.getController()).isNotNull();
      assertThat(exporter.getController()).isEqualTo(exporterContainer);
    }

    @Test
    void shouldInitPositionToDefaultIfNotExistInState() throws Exception {
      // given
      exporterContainer.configureExporter();

      // when
      exporterContainer.initMetadata();

      // then
      assertThat(exporterContainer.getPosition()).isEqualTo(-1);
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(-1);
    }

    @Test
    void shouldInitPositionWithStateValues() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0xCAFE);

      // when
      exporterContainer.initMetadata();

      // then
      assertThat(exporterContainer.getPosition()).isEqualTo(0xCAFE);
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(0xCAFE);
    }

    @Test
    void shouldNotExportWhenRecordPositionIsSmaller() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0xCAFE);
      exporterContainer.initMetadata();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();

      // when
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      // then
      assertThat(exporter.getRecord()).isNull();
    }

    @Test
    void shouldUpdateUnacknowledgedPositionOnExport() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();

      // when
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      // then
      assertThat(exporter.getRecord()).isNotNull();
      assertThat(exporter.getRecord()).isEqualTo(mockedRecord);
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
      assertThat(exporterContainer.getPosition()).isZero();
    }

    @Test
    void shouldUpdateUnacknowledgedPositionMultipleTimes() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      // when
      final var secondRecord = mock(TypedRecord.class);
      when(secondRecord.getPosition()).thenReturn(2L);
      exporterContainer.exportRecord(recordMetadata, secondRecord);

      // then
      assertThat(exporter.getRecord()).isNotNull();
      assertThat(exporter.getRecord()).isEqualTo(secondRecord);
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(2);
      assertThat(exporterContainer.getPosition()).isZero();
    }

    @Test
    void shouldUpdateExporterPosition() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();
      exporterContainer.openExporter();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      // when
      exporterContainer.updateLastExportedRecordPosition(mockedRecord.getPosition());
      awaitPreviousCall();

      // then
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
      assertThat(exporterContainer.getPosition()).isEqualTo(1);
      assertThat(runtime.getState().getPosition(EXPORTER_ID)).isEqualTo(1);
    }

    @Test
    void shouldNotUpdateExporterPositionToSmallerValue() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();
      exporterContainer.openExporter();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      // when
      exporterContainer.updateLastExportedRecordPosition(-1);
      awaitPreviousCall();

      // then
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
      assertThat(exporterContainer.getPosition()).isZero();
      assertThat(runtime.getState().getPosition(EXPORTER_ID)).isZero();
    }

    @Test
    void shouldNotUpdateExporterPositionInDifferentOrder() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();
      exporterContainer.openExporter();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();
      exporterContainer.exportRecord(recordMetadata, mockedRecord);
      when(mockedRecord.getPosition()).thenReturn(2L);
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      // when
      exporterContainer.updateLastExportedRecordPosition(2);
      exporterContainer.updateLastExportedRecordPosition(1);
      awaitPreviousCall();

      // then
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(2);
      assertThat(exporterContainer.getPosition()).isEqualTo(2);
      assertThat(runtime.getState().getPosition(EXPORTER_ID)).isEqualTo(2);
    }

    @Test
    void shouldNotUpdateExporterPositionIfSoftPaused() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();
      exporterContainer.openExporter();
      exporterContainer.softPauseExporter();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      // when
      exporterContainer.updateLastExportedRecordPosition(mockedRecord.getPosition());
      awaitPreviousCall();

      // then
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
      assertThat(exporterContainer.getPosition()).isZero();
    }

    @Test
    void shouldUpdatePositionWhenResumedAfterSoftPaused() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();
      exporterContainer.openExporter();
      exporterContainer.softPauseExporter();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final byte[] metadata = "metadata".getBytes();
      final var recordMetadata = new RecordMetadata().requestId(1L);
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      exporterContainer.updateLastExportedRecordPosition(mockedRecord.getPosition(), metadata);
      awaitPreviousCall();

      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
      assertThat(exporterContainer.getPosition()).isZero();
      assertThat(exporterContainer.readMetadata()).isNotPresent();

      // when
      exporterContainer.undoSoftPauseExporter();
      awaitPreviousCall();

      // then
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
      assertThat(exporterContainer.getPosition()).isEqualTo(1);
      assertThat(exporterContainer.readMetadata()).isPresent().hasValue(metadata);
    }

    @Test
    void shouldUpdatePositionsWhenRecordIsFiltered() throws Exception {
      // given
      exporterContainer.configureExporter();
      exporter.getContext().setFilter(new AlwaysRejectingFilter());
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();

      // when
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      // then
      assertThat(exporter.getRecord()).isNull();
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isZero();
      assertThat(exporterContainer.getPosition()).isEqualTo(1);
    }

    @Test
    void shouldUpdatePositionsWhenRecordIsFilteredAndPositionsAreEqual() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();

      final var mockedRecord = mock(TypedRecord.class);
      when(mockedRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();
      exporterContainer.exportRecord(recordMetadata, mockedRecord);
      exporterContainer.updateLastExportedRecordPosition(mockedRecord.getPosition());
      awaitPreviousCall();

      // when
      exporter.getContext().setFilter(new AlwaysRejectingFilter());
      when(mockedRecord.getPosition()).thenReturn(2L);
      exporterContainer.exportRecord(recordMetadata, mockedRecord);

      // then
      assertThat(exporter.getRecord()).isNotNull();
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
      assertThat(exporterContainer.getPosition()).isEqualTo(2);
    }

    @Test
    void shouldNotUpdatePositionsWhenRecordIsFilteredAndLastEventWasUnacknowledged()
        throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();

      final var firstRecord = mock(TypedRecord.class);
      when(firstRecord.getPosition()).thenReturn(1L);
      final var recordMetadata = new RecordMetadata();
      exporterContainer.exportRecord(recordMetadata, firstRecord);

      // when
      final var secondRecord = mock(TypedRecord.class);
      when(secondRecord.getPosition()).thenReturn(2L);
      exporter.getContext().setFilter(new AlwaysRejectingFilter());
      exporterContainer.exportRecord(recordMetadata, secondRecord);

      // then
      assertThat(exporter.getRecord()).isNotNull();
      assertThat(exporter.getRecord()).isEqualTo(firstRecord);
      assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
      assertThat(exporterContainer.getPosition()).isZero();
    }

    @Test
    void shouldCloseExporter() throws Exception {
      // given
      exporterContainer.configureExporter();
      runtime.getState().setPosition(EXPORTER_ID, 0);
      exporterContainer.initMetadata();

      // when
      exporterContainer.close();

      // then
      assertThat(exporter.isClosed()).isTrue();
    }

    @Test
    void shouldReturnEmptyMetadataIfNotExistInState() throws Exception {
      // given
      exporterContainer.configureExporter();

      // when
      final var metadata = exporterContainer.readMetadata();

      // then
      assertThat(metadata).isNotPresent();
    }

    @Test
    void shouldReadMetadataFromState() throws Exception {
      // given
      exporterContainer.configureExporter();

      final var metadata = "metadata".getBytes();
      runtime.getState().setExporterState(EXPORTER_ID, 10, BufferUtil.wrapArray(metadata));

      // when
      final var readMetadata = exporterContainer.readMetadata();

      // then
      assertThat(readMetadata).isPresent().hasValue(metadata);
    }

    @Test
    void shouldStoreMetadataInState() throws Exception {
      // given
      exporterContainer.configureExporter();

      // when
      final var metadata = "metadata".getBytes();
      exporterContainer.updateLastExportedRecordPosition(10, metadata);
      awaitPreviousCall();

      // then
      final var metadataInState = runtime.getState().getExporterMetadata(EXPORTER_ID);
      assertThat(metadataInState).isNotNull().isEqualTo(BufferUtil.wrapArray(metadata));
    }

    @Test
    void shouldNotUpdateMetadataInStateIfPositionIsSmaller() throws Exception {
      // given
      exporterContainer.configureExporter();

      final var metadataBefore = "m1".getBytes();
      exporterContainer.updateLastExportedRecordPosition(20, metadataBefore);
      awaitPreviousCall();

      // when
      final var metadataUpdated = "m2".getBytes();
      exporterContainer.updateLastExportedRecordPosition(10, metadataUpdated);
      awaitPreviousCall();

      // then
      final var metadataInState = runtime.getState().getExporterMetadata(EXPORTER_ID);
      assertThat(metadataInState).isNotNull().isEqualTo(BufferUtil.wrapArray(metadataBefore));
    }

    @Test
    void shouldStoreAndReadMetadata() throws Exception {
      // given
      exporterContainer.configureExporter();

      final var metadata = "metadata".getBytes();

      // when
      exporterContainer.updateLastExportedRecordPosition(10, metadata);
      awaitPreviousCall();

      final var readMetadata = exporterContainer.readMetadata();

      // then
      assertThat(readMetadata).isPresent().hasValue(metadata);
    }

    private void awaitPreviousCall() {
      // call is enqueued in queue and will be run after the previous call
      // when we await the call we can be sure that the previous call is also done
      runtime.getActor().getActorControl().call(() -> null).join();
    }
  }

  @Nested
  class WithInitializationInfo {
    private static final String OTHER_EXPORTER_ID = "otherExporter";
    private ExporterDescriptor descriptor;

    @BeforeEach
    void beforeEach(final @TempDir Path storagePath) throws ExporterLoadException {
      runtime = new ExporterContainerRuntime(storagePath);

      descriptor =
          runtime
              .getRepository()
              .validateAndAddExporterDescriptor(
                  EXPORTER_ID, FakeExporter.class, Map.of("key", "value"));
    }

    @Test
    void shouldInitializeWithGivenMetadataVersion() throws Exception {
      // given
      exporterContainer =
          runtime.newContainer(descriptor, PARTITION_ID, new ExporterInitializationInfo(10, null));

      exporterContainer.configureExporter();

      // when
      exporterContainer.initMetadata();

      // then
      assertThat(exporterContainer.getPosition())
          .describedAs("Position is initialized")
          .isEqualTo(-1);
      assertThat(exporterContainer.getLastUnacknowledgedPosition())
          .describedAs("LastUnacknowledgedPosition is initialized")
          .isEqualTo(-1);
      assertThat(runtime.getState().getMetadataVersion(EXPORTER_ID))
          .describedAs("MetadataVersion is initialized")
          .isEqualTo(10);
    }

    @Test
    void shouldReInitializeWithGivenMetadataVersion() throws Exception {
      // given
      runtime.getState().setExporterState(EXPORTER_ID, 5, null);
      exporterContainer =
          runtime.newContainer(descriptor, PARTITION_ID, new ExporterInitializationInfo(10, null));

      exporterContainer.configureExporter();

      // when
      exporterContainer.initMetadata();

      // then
      assertThat(exporterContainer.getPosition())
          .describedAs("Position is initialized")
          .isEqualTo(-1);
      assertThat(exporterContainer.getLastUnacknowledgedPosition())
          .describedAs("LastUnacknowledgedPosition is initialized")
          .isEqualTo(-1);
      assertThat(runtime.getState().getMetadataVersion(EXPORTER_ID))
          .describedAs("MetadataVersion is initialized")
          .isEqualTo(10);
    }

    @Test
    void shouldInitializeWithGivenExporterMetadata() throws Exception {
      // given
      final var metadata = BufferUtil.wrapString("other-metadata");
      runtime.getState().setExporterState(OTHER_EXPORTER_ID, 5, metadata);
      exporterContainer =
          runtime.newContainer(
              descriptor, PARTITION_ID, new ExporterInitializationInfo(1, OTHER_EXPORTER_ID));

      exporterContainer.configureExporter();

      // when
      exporterContainer.initMetadata();

      // then
      assertThat(exporterContainer.getPosition())
          .describedAs("Position is initialized")
          .isEqualTo(5);
      assertThat(exporterContainer.getLastUnacknowledgedPosition())
          .describedAs("LastUnacknowledgedPosition is initialized")
          .isEqualTo(5);
      assertThat(runtime.getState().getMetadataVersion(EXPORTER_ID))
          .describedAs("MetadataVersion is initialized")
          .isEqualTo(1);
      assertThat(BufferUtil.bufferAsString(runtime.getState().getExporterMetadata(EXPORTER_ID)))
          .describedAs("Metadata is initialized from the other exporter")
          .isEqualTo("other-metadata");
    }

    @Test
    void shouldReInitializeWithGivenExporterMetadata() throws Exception {
      // given
      final var metadata = BufferUtil.wrapString("other-metadata");
      runtime.getState().setExporterState(OTHER_EXPORTER_ID, 5, metadata);
      runtime.getState().initializeExporterState(EXPORTER_ID, 1, null, 2);
      exporterContainer =
          runtime.newContainer(
              descriptor, PARTITION_ID, new ExporterInitializationInfo(3, OTHER_EXPORTER_ID));

      exporterContainer.configureExporter();

      // when
      exporterContainer.initMetadata();

      // then
      assertThat(exporterContainer.getPosition())
          .describedAs("Position is initialized")
          .isEqualTo(5);
      assertThat(exporterContainer.getLastUnacknowledgedPosition())
          .describedAs("LastUnacknowledgedPosition is initialized")
          .isEqualTo(5);
      assertThat(runtime.getState().getMetadataVersion(EXPORTER_ID))
          .describedAs("MetadataVersion is initialized")
          .isEqualTo(3);
      assertThat(BufferUtil.bufferAsString(runtime.getState().getExporterMetadata(EXPORTER_ID)))
          .describedAs("Metadata is initialized from the other exporter")
          .isEqualTo("other-metadata");
    }

    @Test
    void shouldNotOverwriteMetadataVersion() throws Exception {
      // given
      exporterContainer =
          runtime.newContainer(descriptor, PARTITION_ID, new ExporterInitializationInfo(3, null));

      exporterContainer.configureExporter();
      exporterContainer.initMetadata();

      // when
      exporterContainer.updateLastExportedRecordPosition(15);

      // then

      Awaitility.await()
          .untilAsserted(
              () ->
                  assertThat(exporterContainer.getPosition())
                      .describedAs("Position is updated")
                      .isEqualTo(15));
      assertThat(runtime.getState().getPosition(EXPORTER_ID)).isEqualTo(15);
      assertThat(runtime.getState().getMetadataVersion(EXPORTER_ID))
          .describedAs("MetadataVersion is not changed")
          .isEqualTo(3);
    }

    @Test
    void shouldAddMeterToMeterRegistryGivenInContext() throws Exception {
      // given
      descriptor =
          runtime
              .getRepository()
              .validateAndAddExporterDescriptor(
                  "fakeExporterWithMetrics", FakeExporterWithMetrics.class, Map.of("key", "value"));

      final var meterRegistry = new SimpleMeterRegistry();
      exporterContainer =
          runtime.newContainer(
              descriptor, PARTITION_ID, new ExporterInitializationInfo(1, null), meterRegistry);

      exporterContainer.configureExporter();
      exporterContainer.initMetadata();

      // when
      // then
      assertThat(meterRegistry.getMeters().getFirst().getId().getName())
          .isEqualTo(REGISTERED_COUNTER_NAME);
    }
  }
}
