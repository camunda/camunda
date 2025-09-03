/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics;

import static io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor.FRAME_ALIGNMENT;
import static io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType.TU;
import static io.camunda.zeebe.util.StreamProcessingConstants.DEFAULT_MAX_FRAGMENT_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.metrics.DbUsageMetricState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.stream.TestBufferedProcessingResultBuilder;
import io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.HashUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class UsageMetricsRecordDivideTest {

  private static final String TENANT_ID = "tenant";

  private UsageMetricsExportProcessor processor;
  private Method appendFollowUpEventMethod;
  private Method splitAndAppendRecordHybridMethod;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private TestBufferedProcessingResultBuilder<UsageMetricRecord> bufferedProcessingResultBuilder;
  private MutableUsageMetricState state;
  private MutableProcessingState processingState;

  @BeforeEach
  void setUp() throws Exception {
    // Initialize the mock log stream writer
    final LogStreamWriter logStreamWriter =
        new LogStreamWriter() {
          @Override
          public boolean canWriteEvents(final int eventCount, final int batchSize) {
            final int framedMessageLength =
                batchSize
                    + eventCount * (DataFrameDescriptor.HEADER_LENGTH + FRAME_ALIGNMENT)
                    + FRAME_ALIGNMENT;
            return framedMessageLength <= DEFAULT_MAX_FRAGMENT_SIZE;
          }

          @Override
          public Either<WriteFailure, Long> tryWrite(
              final WriteContext context,
              final List<LogAppendEntry> appendEntries,
              final long sourcePosition) {
            return Either.right(1L);
          }
        };

    state = new DbUsageMetricState(zeebeDb, transactionContext, Duration.ofSeconds(1));
    state.resetActiveBucket(1L);
    final var keyGenerator =
        new DbKeyGenerator(Protocol.DEPLOYMENT_PARTITION, zeebeDb, transactionContext);
    final var eventAppliers = new EventAppliers().registerEventAppliers(processingState);

    // Initialize the fake processing result builder
    bufferedProcessingResultBuilder =
        new TestBufferedProcessingResultBuilder<>(logStreamWriter::canWriteEvents);

    final Writers writers = new Writers(() -> bufferedProcessingResultBuilder, eventAppliers);
    processor = new UsageMetricsExportProcessor(state, writers, keyGenerator);

    // Use reflection to access the private method
    appendFollowUpEventMethod =
        UsageMetricsExportProcessor.class.getDeclaredMethod(
            "appendFollowUpEvent", UsageMetricRecord.class);
    appendFollowUpEventMethod.setAccessible(true);
    splitAndAppendRecordHybridMethod =
        UsageMetricsExportProcessor.class.getDeclaredMethod(
            "splitAndAppendRecordHybrid", UsageMetricRecord.class);
    splitAndAppendRecordHybridMethod.setAccessible(true);
  }

  @Test
  void shouldAppendOneFollowUpEventWithManyAssignees() throws Exception {
    // given
    final UsageMetricRecord eventRecord = createLargeSetValueRecord(1, 465_000);

    // when
    appendFollowUpEventMethod.invoke(processor, eventRecord);

    // then
    assertThat(bufferedProcessingResultBuilder.getFollowupEventRecords()).hasSize(1);
    assertThat(bufferedProcessingResultBuilder.getFollowupCommandRecords()).isEmpty();

    final var recordedEvent = bufferedProcessingResultBuilder.getFollowupEventRecords().getFirst();
    assertThat(recordedEvent.getIntent()).isEqualTo(UsageMetricIntent.EXPORTED);
    assertThat(recordedEvent.getValue()).isInstanceOf(UsageMetricRecord.class);

    final UsageMetricRecord capturedRecord = recordedEvent.getValue();
    assertThat(capturedRecord.getEventType()).isEqualTo(EventType.TU);
    assertThat(capturedRecord.getIntervalType()).isEqualTo(IntervalType.ACTIVE);
    assertThat(capturedRecord.getResetTime()).isEqualTo(123L);
    assertThat(capturedRecord.getStartTime()).isEqualTo(100L);
    assertThat(capturedRecord.getEndTime()).isEqualTo(200L);
    assertThat(capturedRecord.getSetValues().get(TENANT_ID)).size().isEqualTo(465_000);
  }

  @Test
  void shouldAppendOneFollowUpEventWithManyTenants() throws Exception {
    // given
    final UsageMetricRecord eventRecord = createLargeSetValueRecord(20, 20_000);

    // when
    appendFollowUpEventMethod.invoke(processor, eventRecord);

    // then
    assertThat(bufferedProcessingResultBuilder.getFollowupEventRecords()).hasSize(1);
    assertThat(bufferedProcessingResultBuilder.getFollowupCommandRecords()).isEmpty();

    final var recordedEvent = bufferedProcessingResultBuilder.getFollowupEventRecords().getFirst();
    assertThat(recordedEvent.getIntent()).isEqualTo(UsageMetricIntent.EXPORTED);
    assertThat(recordedEvent.getValue()).isInstanceOf(UsageMetricRecord.class);

    final UsageMetricRecord capturedRecord = recordedEvent.getValue();
    assertThat(capturedRecord.getEventType()).isEqualTo(EventType.TU);
    assertThat(capturedRecord.getIntervalType()).isEqualTo(IntervalType.ACTIVE);
    assertThat(capturedRecord.getResetTime()).isEqualTo(123L);
    assertThat(capturedRecord.getStartTime()).isEqualTo(100L);
    assertThat(capturedRecord.getEndTime()).isEqualTo(200L);
    assertThat(capturedRecord.getSetValues()).size().isEqualTo(20);
  }

  @Test
  void shouldAppendFollowUpEventsManyAssignees() throws Exception {
    // given
    final UsageMetricRecord eventRecord = createLargeSetValueRecord(1, 470_000);

    // when
    splitAndAppendRecordHybridMethod.invoke(processor, eventRecord);

    // then
    assertThat(bufferedProcessingResultBuilder.getFollowupEventRecords()).hasSize(4);
    assertThat(bufferedProcessingResultBuilder.getFollowupCommandRecords()).hasSize(1);
  }

  @Test
  void shouldAppendFollowUpEventsManyTenants() throws Exception {
    // given
    final UsageMetricRecord eventRecord = createLargeSetValueRecord(20, 23_500);

    // when
    splitAndAppendRecordHybridMethod.invoke(processor, eventRecord);

    // then
    assertThat(bufferedProcessingResultBuilder.getFollowupEventRecords()).hasSize(4);
    assertThat(bufferedProcessingResultBuilder.getFollowupCommandRecords()).hasSize(1);
  }

  @Test
  void shouldAppendFollowUpEventsAndFollowUpCommandsManyAssignees() throws Exception {
    // given
    final UsageMetricRecord eventRecord = createLargeSetValueRecord(1, 600_000);

    // when
    splitAndAppendRecordHybridMethod.invoke(processor, eventRecord);

    // then
    assertThat(bufferedProcessingResultBuilder.getFollowupEventRecords()).hasSize(3);
    assertThat(bufferedProcessingResultBuilder.getFollowupCommandRecords()).hasSize(1);
  }

  @Test
  void shouldAppendFollowUpEventsAndFollowUpCommandsManyTenants() throws Exception {
    // given
    final UsageMetricRecord eventRecord = createLargeSetValueRecord(20, 40_000);

    // when
    splitAndAppendRecordHybridMethod.invoke(processor, eventRecord);

    // then
    assertThat(bufferedProcessingResultBuilder.getFollowupEventRecords()).hasSize(2);
    assertThat(bufferedProcessingResultBuilder.getFollowupCommandRecords()).hasSize(1);
  }

  private UsageMetricRecord createLargeSetValueRecord(
      final int tenantsSize, final int assigneeSize) {
    return new UsageMetricRecord()
        .setEventType(TU)
        .setIntervalType(IntervalType.ACTIVE)
        .setResetTime(123L)
        .setStartTime(100L)
        .setEndTime(200L)
        .setSetValues(
            BufferUtil.wrapArray(
                MsgPackConverter.convertToMsgPack(generateLargeSetMap(tenantsSize, assigneeSize))));
  }

  private static Map<String, Set<Long>> generateLargeSetMap(
      final int tenantsSize, final int assigneeSize) {
    final var map = new HashMap<String, Set<Long>>();
    final var set = new HashSet<Long>();
    if (tenantsSize > 1) {
      for (int i = 0; i < assigneeSize; i++) {
        set.add(HashUtil.getStringHashValue(String.valueOf(i)));
      }
      for (int i = 0; i < tenantsSize; i++) {
        map.put(TENANT_ID + i, set);
      }

    } else {
      for (int i = 0; i < assigneeSize; i++) {
        set.add(HashUtil.getStringHashValue(String.valueOf(i)));
      }
      map.put(TENANT_ID, set);
    }
    return map;
  }
}
