/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ExporterContainerTest {

  @Rule public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();
  private TestActor testActor;
  private ExportersState exportersState;
  private FakeExporter exporter;
  private ExporterContainer exporterContainer;

  @Before
  public void setup() throws IOException {
    testActor = new TestActor();
    actorSchedulerRule.submitActor(testActor).join();
    final var dbFactory = DefaultZeebeDbFactory.defaultFactory();
    final var db = dbFactory.createDb(tempFolder.newFolder());
    exportersState = new ExportersState(db, db.createContext());
    final ExporterMetrics exporterMetrics = new ExporterMetrics(1);

    final var exporterDescriptor =
        new ExporterDescriptor("fakeExporter", FakeExporter.class, Map.of("key", "value"));
    exporterContainer = new ExporterContainer(exporterDescriptor);
    exporter = (FakeExporter) exporterContainer.getExporter();
    exporterContainer.initContainer(testActor.getActor(), exporterMetrics, exportersState);
  }

  @Test
  public void shouldConfigureExporter() throws Exception {
    // given

    // when
    exporterContainer.configureExporter();

    // then
    assertThat(exporter.getContext()).isNotNull();
    assertThat(exporter.getContext().getLogger()).isNotNull();
    assertThat(exporter.getContext().getConfiguration()).isNotNull();
    assertThat(exporter.getContext().getConfiguration().getId()).isEqualTo("fakeExporter");
    assertThat(exporter.getContext().getConfiguration().getArguments())
        .isEqualTo(Map.of("key", "value"));
  }

  @Test
  public void shouldOpenExporter() throws Exception {
    // given
    exporterContainer.configureExporter();

    // when
    exporterContainer.openExporter();

    // then
    assertThat(exporter.getController()).isNotNull();
    assertThat(exporter.getController()).isEqualTo(exporterContainer);
  }

  @Test
  public void shouldInitPositionToDefaultIfNotExistInState() throws Exception {
    // given
    exporterContainer.configureExporter();

    // when
    exporterContainer.initPosition();

    // then
    assertThat(exporterContainer.getPosition()).isEqualTo(-1);
    assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(-1);
  }

  @Test
  public void shouldInitPositionWithStateValues() throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0xCAFE);

    // when
    exporterContainer.initPosition();

    // then
    assertThat(exporterContainer.getPosition()).isEqualTo(0xCAFE);
    assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(0xCAFE);
  }

  @Test
  public void shouldNotExportWhenRecordPositionIsSmaller() throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0xCAFE);
    exporterContainer.initPosition();

    final var mockedRecord = mock(TypedRecord.class);
    when(mockedRecord.getPosition()).thenReturn(1L);
    final var recordMetadata = new RecordMetadata();

    // when
    exporterContainer.exportRecord(recordMetadata, mockedRecord);

    // then
    assertThat(exporter.getRecord()).isNull();
  }

  @Test
  public void shouldUpdateUnacknowledgedPositionOnExport() throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0);
    exporterContainer.initPosition();

    final var mockedRecord = mock(TypedRecord.class);
    when(mockedRecord.getPosition()).thenReturn(1L);
    final var recordMetadata = new RecordMetadata();

    // when
    exporterContainer.exportRecord(recordMetadata, mockedRecord);

    // then
    assertThat(exporter.getRecord()).isNotNull();
    assertThat(exporter.getRecord()).isEqualTo(mockedRecord);
    assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
    assertThat(exporterContainer.getPosition()).isEqualTo(0);
  }

  @Test
  public void shouldUpdateUnacknowledgedPositionMultipleTimes() throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0);
    exporterContainer.initPosition();

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
    assertThat(exporterContainer.getPosition()).isEqualTo(0);
  }

  @Test
  public void shouldUpdateExporterPosition() throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0);
    exporterContainer.initPosition();
    exporterContainer.openExporter();

    final var mockedRecord = mock(TypedRecord.class);
    when(mockedRecord.getPosition()).thenReturn(1L);
    final var recordMetadata = new RecordMetadata();
    exporterContainer.exportRecord(recordMetadata, mockedRecord);

    // when
    exporterContainer.updateLastExportedRecordPosition(mockedRecord.getPosition());
    testActor.awaitPreviousCall();

    // then
    assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
    assertThat(exporterContainer.getPosition()).isEqualTo(1);
    assertThat(exportersState.getPosition("fakeExporter")).isEqualTo(1);
  }

  @Test
  public void shouldNotUpdateExporterPositionToSmallerValue() throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0);
    exporterContainer.initPosition();
    exporterContainer.openExporter();

    final var mockedRecord = mock(TypedRecord.class);
    when(mockedRecord.getPosition()).thenReturn(1L);
    final var recordMetadata = new RecordMetadata();
    exporterContainer.exportRecord(recordMetadata, mockedRecord);

    // when
    exporterContainer.updateLastExportedRecordPosition(-1);
    testActor.awaitPreviousCall();

    // then
    assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(1);
    assertThat(exporterContainer.getPosition()).isEqualTo(0);
    assertThat(exportersState.getPosition("fakeExporter")).isEqualTo(0);
  }

  @Test
  public void shouldNotUpdateExporterPositionInDifferentOrder() throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0);
    exporterContainer.initPosition();
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
    testActor.awaitPreviousCall();

    // then
    assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(2);
    assertThat(exporterContainer.getPosition()).isEqualTo(2);
    assertThat(exportersState.getPosition("fakeExporter")).isEqualTo(2);
  }

  @Test
  public void shouldUpdatePositionsWhenRecordIsFiltered() throws Exception {
    // given
    exporterContainer.configureExporter();
    exporter.getContext().setFilter(new AlwaysRejectingFilter());
    exportersState.setPosition("fakeExporter", 0);
    exporterContainer.initPosition();

    final var mockedRecord = mock(TypedRecord.class);
    when(mockedRecord.getPosition()).thenReturn(1L);
    final var recordMetadata = new RecordMetadata();

    // when
    exporterContainer.exportRecord(recordMetadata, mockedRecord);

    // then
    assertThat(exporter.getRecord()).isNull();
    assertThat(exporterContainer.getLastUnacknowledgedPosition()).isEqualTo(0);
    assertThat(exporterContainer.getPosition()).isEqualTo(1);
  }

  @Test
  public void shouldUpdatePositionsWhenRecordIsFilteredAndPositionsAreEqual() throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0);
    exporterContainer.initPosition();

    final var mockedRecord = mock(TypedRecord.class);
    when(mockedRecord.getPosition()).thenReturn(1L);
    final var recordMetadata = new RecordMetadata();
    exporterContainer.exportRecord(recordMetadata, mockedRecord);
    exporterContainer.updateLastExportedRecordPosition(mockedRecord.getPosition());
    testActor.awaitPreviousCall();

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
  public void shouldNotUpdatePositionsWhenRecordIsFilteredAndLastEventWasUnacknowledged()
      throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0);
    exporterContainer.initPosition();

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
    assertThat(exporterContainer.getPosition()).isEqualTo(0);
  }

  @Test
  public void shouldCloseExporter() throws Exception {
    // given
    exporterContainer.configureExporter();
    exportersState.setPosition("fakeExporter", 0);
    exporterContainer.initPosition();

    // when
    exporterContainer.close();

    // then
    assertThat(exporter.isClosed()).isTrue();
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

  private static final class TestActor extends Actor {

    public ActorControl getActor() {
      return actor;
    }

    public void awaitPreviousCall() {
      // call is enqueued in queue and will be run after the previous call
      // when we await the call we can be sure that the previous call is also done
      actor.call(() -> null).join();
    }
  }

  public static final class FakeExporter implements Exporter {

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
}
