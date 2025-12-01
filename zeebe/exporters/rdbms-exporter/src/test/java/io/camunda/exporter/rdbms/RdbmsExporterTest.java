/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.PostFlushListener;
import io.camunda.db.rdbms.write.queue.PreFlushListener;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.QueueItemMerger;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RdbmsExporterTest {

  private Controller controller;
  private RdbmsWriter rdbmsWriter;
  private RdbmsExporter exporter;
  private StubExecutionQueue executionQueue;
  private ExporterPositionService positionService;
  private HistoryCleanupService historyCleanupService;
  private RdbmsWriterMetrics metrics;

  private ScheduledTask flushTask;
  private ScheduledTask cleanupTask;
  private ScheduledTask usageMetricsCleanupTask;
  private RdbmsPurger rdbmsPurger;

  @Test
  void shouldCallCorrectHandler() {
    // given
    final var jobHandler = mockHandler(ValueType.JOB);
    final var otherJobHandler = mockHandler(ValueType.JOB, false);
    final var piHandler = mockHandler(ValueType.PROCESS_INSTANCE);
    final var record = mockRecord(ValueType.JOB, 1);

    createExporter(
        b ->
            b.withHandler(ValueType.JOB, jobHandler)
                .withHandler(ValueType.JOB, otherJobHandler)
                .withHandler(ValueType.PROCESS_INSTANCE, piHandler));

    // when
    exporter.export(record);

    // then
    verify(jobHandler).canExport(record);
    verify(otherJobHandler).canExport(record);
    verify(piHandler, never()).canExport(record);
    verify(jobHandler).export(record);
    verify(otherJobHandler, never()).export(record);
    verify(piHandler, never()).export(record);
  }

  @Test
  void shouldCheckForFlushOnProcessedRecord() {
    // given
    final var jobHandler = mockHandler(ValueType.JOB);
    final var record = mockRecord(ValueType.JOB, 1);

    createExporter(b -> b.withHandler(ValueType.JOB, jobHandler));

    // when
    exporter.export(record);

    // then
    verify(rdbmsWriter).flush(anyBoolean());
  }

  @Test
  void shouldNotCheckForFlushOnNotProcessedRecord() {
    // given
    final var jobHandler = mockHandler(ValueType.JOB, false);
    final var record = mockRecord(ValueType.JOB, 1);

    createExporter(b -> b.withHandler(ValueType.JOB, jobHandler));

    // when
    exporter.export(record);

    // then
    verify(rdbmsWriter, never()).flush(anyBoolean());
  }

  @Test
  void shouldNotCheckForFlushOnNoHandler() {
    // given
    final var jobHandler = mockHandler(ValueType.PROCESS_INSTANCE);
    final var record = mockRecord(ValueType.JOB, 1);

    createExporter(b -> b.withHandler(ValueType.JOB, jobHandler));

    // when
    exporter.export(record);

    // then
    verify(rdbmsWriter, never()).flush(anyBoolean());
  }

  @Test
  void shouldRegisterFlushListeners() {
    // given
    createExporter(b -> b);

    // then
    assertThat(executionQueue.preFlushListeners).isNotEmpty();
    assertThat(executionQueue.postFlushListeners).isNotEmpty();
  }

  @Test
  void shouldUpdatePositionOnFlush() {
    // given
    createExporter(b -> b.withHandler(ValueType.JOB, mockHandler(ValueType.JOB)));

    // when
    exporter.export(mockRecord(ValueType.JOB, 1));
    exporter.export(mockRecord(ValueType.JOB, 2));
    executionQueue.flush();

    // then
    verify(positionService).update(Mockito.argThat(p -> p.lastExportedPosition() == 2));
  }

  @Test
  void shouldNotUpdatePositionOnFlushWhenNoRecordsHandled() {
    // given
    createExporter(b -> b);

    // when
    exporter.export(mockRecord(ValueType.JOB, 1));
    exporter.export(mockRecord(ValueType.JOB, 2));
    executionQueue.flush();

    // then
    verify(positionService, never()).update(any());
  }

  @Test
  void shouldUpdatePositionAfterEachRecordWhenQueueSizeIsZero() {
    // given
    createExporter(b -> b.queueSize(0).withHandler(ValueType.JOB, mockHandler(ValueType.JOB)));

    // when
    exporter.export(mockRecord(ValueType.JOB, 1));
    exporter.export(mockRecord(ValueType.JOB, 2));
    executionQueue.flush();

    // then
    verify(positionService, times(2)).update(any());
    verify(positionService).update(Mockito.argThat(p -> p.lastExportedPosition() == 1));
    verify(positionService).update(Mockito.argThat(p -> p.lastExportedPosition() == 2));
  }

  @Test
  void shouldUpdatePositionAfterEachRecordWhenFlushIntervalIsZero() {
    // given
    createExporter(
        b -> b.flushInterval(Duration.ZERO).withHandler(ValueType.JOB, mockHandler(ValueType.JOB)));

    // when
    exporter.export(mockRecord(ValueType.JOB, 1));
    exporter.export(mockRecord(ValueType.JOB, 2));
    executionQueue.flush();

    // then
    verify(positionService, times(2)).update(any());
    verify(positionService).update(Mockito.argThat(p -> p.lastExportedPosition() == 1));
    verify(positionService).update(Mockito.argThat(p -> p.lastExportedPosition() == 2));
  }

  @Test
  void shouldFlushOnClose() {
    // given
    createExporter(b -> b.withHandler(ValueType.JOB, mockHandler(ValueType.JOB)));

    // when
    exporter.export(mockRecord(ValueType.JOB, 1));
    exporter.close();

    // then
    verify(positionService).update(Mockito.argThat(p -> p.lastExportedPosition() == 1));
  }

  @Test
  void shouldRegisterFlushIntervalTimer() {
    // given
    createExporter(b -> b);

    // then
    verify(controller).scheduleCancellableTask(eq(Duration.ofMillis(500)), any());
  }

  @Test
  void shouldNotRegisterFlushIntervalTimerWhenQueueSizeIsZero() {
    // given
    createExporter(b -> b.queueSize(0));

    // then
    // only cleanup task
    verify(controller, times(2)).scheduleCancellableTask(any(), any());
  }

  @Test
  void shouldNotRegisterFlushIntervalTimerWhenFlushIntervalIsZero() {
    // given
    createExporter(b -> b.flushInterval(Duration.ZERO));

    // then
    // only cleanup task
    verify(controller, times(2)).scheduleCancellableTask(any(), any());
  }

  @Test
  void shouldFlushAndRescheduleWhenTimerTaskIsExecuted() {
    // given
    createExporter(b -> b.withHandler(ValueType.JOB, mockHandler(ValueType.JOB)));
    final var runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(controller, times(3))
        .scheduleCancellableTask(any(Duration.class), runnableCaptor.capture());
    final var runnable = runnableCaptor.getAllValues().getFirst();

    // when
    exporter.export(mockRecord(ValueType.JOB, 1));
    runnable.run();

    // then
    // captures the initial and the rescheduled call
    verify(controller, times(4)).scheduleCancellableTask(any(Duration.class), any());
    verify(positionService).update(Mockito.argThat(p -> p.lastExportedPosition() == 1));
  }

  @Test
  void shouldClearFlushTaskOnPurge() {
    // given
    createExporter(b -> b);

    // when
    exporter.purge();

    // then
    verify(flushTask).cancel();
    verify(rdbmsPurger).purgeRdbms();
  }

  @Test
  void shouldTrackRecordExportingLatency() {
    // given
    createExporter(b -> b.withHandler(ValueType.JOB, mockHandler(ValueType.JOB)));

    final var record = mockRecord(ValueType.JOB, 1);
    when(record.getTimestamp()).thenReturn(System.currentTimeMillis() - 100);

    // when
    exporter.export(record);
    executionQueue.flush();

    // then
    verify(metrics).recordExportingLatency(Mockito.longThat(latency -> latency >= 100));
  }

  @Test
  void shouldTrackOldestRecordTimestampInBatch() {
    // given
    createExporter(b -> b.withHandler(ValueType.JOB, mockHandler(ValueType.JOB)));

    final var newerRecord = mockRecord(ValueType.JOB, 1);
    final var olderRecord = mockRecord(ValueType.JOB, 2);
    when(newerRecord.getTimestamp()).thenReturn(System.currentTimeMillis() - 100);
    when(olderRecord.getTimestamp()).thenReturn(System.currentTimeMillis() - 200);

    // when - export newer record first, then older record
    exporter.export(newerRecord);
    exporter.export(olderRecord);
    executionQueue.flush();

    // then - latency should be based on the older record
    verify(metrics).recordExportingLatency(Mockito.longThat(latency -> latency >= 200));
  }

  @Test
  void shouldResetOldestRecordTimestampAfterFlush() {
    // given
    createExporter(b -> b.withHandler(ValueType.JOB, mockHandler(ValueType.JOB)));

    final var firstRecord = mockRecord(ValueType.JOB, 1);
    final var secondRecord = mockRecord(ValueType.JOB, 2);
    when(firstRecord.getTimestamp()).thenReturn(System.currentTimeMillis() - 100);
    when(secondRecord.getTimestamp()).thenReturn(System.currentTimeMillis() - 50);

    // when - export first record and flush
    exporter.export(firstRecord);
    executionQueue.flush();

    // then - first metric should be recorded
    verify(metrics).recordExportingLatency(Mockito.longThat(latency -> latency >= 100));

    // when - export second record and flush
    exporter.export(secondRecord);
    executionQueue.flush();

    // then - second metric should also be recorded (total of 2 calls)
    verify(metrics, times(2)).recordExportingLatency(Mockito.anyLong());
  }

  @Test
  void shouldNotRecordMetricWhenNoRecordsExported() {
    // given
    createExporter(b -> b.withHandler(ValueType.JOB, mockHandler(ValueType.JOB, false)));

    final var record = mockRecord(ValueType.JOB, 1);
    when(record.getTimestamp()).thenReturn(System.currentTimeMillis());

    // when - record cannot be exported
    exporter.export(record);
    executionQueue.flush();

    // then - metric should not be recorded
    verify(metrics, never()).recordExportingLatency(Mockito.anyLong());
  }

  @Test
  void shouldHandleUnixEpochTimestamp() {
    // given
    createExporter(b -> b.withHandler(ValueType.JOB, mockHandler(ValueType.JOB)));

    final var record = mockRecord(ValueType.JOB, 1);
    when(record.getTimestamp()).thenReturn(0L); // Unix epoch

    // when
    exporter.export(record);
    executionQueue.flush();

    // then - metric should be recorded even for timestamp 0
    verify(metrics).recordExportingLatency(Mockito.anyLong());
  }

  // ------------------------------------------------
  // mocks and stubs
  // ------------------------------------------------

  private RdbmsExportHandler mockHandler(final ValueType valueType) {
    return mockHandler(valueType, true);
  }

  private RdbmsExportHandler mockHandler(final ValueType valueType, final boolean canExport) {
    final var handler = mock(RdbmsExportHandler.class);
    when(handler.canExport(Mockito.argThat(r -> canExport && r.getValueType() == valueType)))
        .thenReturn(true);

    return handler;
  }

  private Record mockRecord(final ValueType valueType, final long position) {
    final var record = mock(Record.class);
    when(record.getValueType()).thenReturn(valueType);
    when(record.getPosition()).thenReturn(position);

    return record;
  }

  private void createExporter(
      final Function<RdbmsExporter.Builder, RdbmsExporter.Builder> builderFunction) {
    flushTask = mock(ScheduledTask.class);
    cleanupTask = mock(ScheduledTask.class);
    usageMetricsCleanupTask = mock(ScheduledTask.class);

    controller = mock(Controller.class);
    when(controller.getLastExportedRecordPosition()).thenReturn(-1L);
    when(controller.scheduleCancellableTask(any(), any()))
        .thenReturn(flushTask)
        .thenReturn(cleanupTask)
        .thenReturn(usageMetricsCleanupTask);

    rdbmsWriter = mock(RdbmsWriter.class);
    executionQueue = new StubExecutionQueue();
    positionService = mock(ExporterPositionService.class);
    when(positionService.findOne(anyInt())).thenReturn(null);
    rdbmsPurger = mock(RdbmsPurger.class);

    historyCleanupService = mock(HistoryCleanupService.class);
    when(historyCleanupService.cleanupHistory(anyInt(), any())).thenReturn(Duration.ofSeconds(1));
    when(historyCleanupService.cleanupUsageMetricsHistory(anyInt(), any()))
        .thenReturn(Duration.ofSeconds(1));

    when(rdbmsWriter.getHistoryCleanupService()).thenReturn(historyCleanupService);

    when(rdbmsWriter.getExporterPositionService()).thenReturn(positionService);
    when(rdbmsWriter.getExecutionQueue()).thenReturn(executionQueue);
    when(rdbmsWriter.getRdbmsPurger()).thenReturn(rdbmsPurger);

    // Mock getMetrics() to return a mock metrics instance by default
    metrics = mock(RdbmsWriterMetrics.class);
    when(rdbmsWriter.getMetrics()).thenReturn(metrics);

    doAnswer((invocation) -> executionQueue.flush()).when(rdbmsWriter).flush(true);
    doAnswer((invocation) -> executionQueue.checkQueueForFlush()).when(rdbmsWriter).flush(false);

    final var builder =
        new RdbmsExporter.Builder()
            .rdbmsWriter(rdbmsWriter)
            .partitionId(0)
            .flushInterval(Duration.ofMillis(500))
            .queueSize(100);

    exporter = builderFunction.apply(builder).build();
    exporter.open(controller);
  }

  private static final class StubExecutionQueue implements ExecutionQueue {

    final List<PreFlushListener> preFlushListeners = new ArrayList<>();
    final List<PostFlushListener> postFlushListeners = new ArrayList<>();

    @Override
    public void executeInQueue(final QueueItem entry) {
      // no-op
    }

    @Override
    public void registerPreFlushListener(final PreFlushListener listener) {
      preFlushListeners.add(listener);
    }

    @Override
    public void registerPostFlushListener(final PostFlushListener listener) {
      postFlushListeners.add(listener);
    }

    @Override
    public int flush() {
      preFlushListeners.forEach(PreFlushListener::onPreFlush);
      postFlushListeners.forEach(PostFlushListener::onPostFlush);
      return 0;
    }

    @Override
    public boolean tryMergeWithExistingQueueItem(final QueueItemMerger combiners) {
      return false;
    }

    @Override
    public boolean checkQueueForFlush() {
      return false;
    }
  }
}
