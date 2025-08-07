/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

final class ElasticsearchExporterTest {

  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final ExporterTestContext context =
      new ExporterTestContext().setConfiguration(new ExporterTestConfiguration<>("test", config));
  private final ExporterTestController controller = new ExporterTestController();
  private final ElasticsearchClient client = mock(ElasticsearchClient.class);
  private final ElasticsearchExporter exporter =
      new ElasticsearchExporter() {
        @Override
        protected ElasticsearchClient createClient() {
          return client;
        }
      };

  @BeforeEach
  void beforeEach() {
    when(client.putIndexTemplate(any())).thenReturn(true);
    when(client.putComponentTemplate()).thenReturn(true);
  }

  @Test
  void shouldNotFailOnOpenIfElasticIsUnreachable() {
    // given
    final var exporter = new ElasticsearchExporter();
    exporter.configure(context);

    // when
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    when(mockRecord.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(mockRecord.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());

    // then
    assertThatThrownBy(() -> exporter.export(mockRecord))
        .isInstanceOf(ElasticsearchExporterException.class);
  }

  @Nested
  final class RecordFilterTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
    void shouldRejectDisabledValueType(final ValueType valueType) {
      // given
      TestSupport.setIndexingForValueType(config.index, valueType, false);

      // when
      exporter.configure(context);

      // then
      final RecordFilter filter = context.getRecordFilter();
      assertThat(filter).isNotNull();
      assertThat(filter.acceptValue(valueType))
          .as("record filter should reject value type based on configuration")
          .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
    void shouldAcceptEnabledValueType(final ValueType valueType) {
      // given
      TestSupport.setIndexingForValueType(config.index, valueType, true);

      // when
      exporter.configure(context);

      // then
      final RecordFilter filter = context.getRecordFilter();
      assertThat(filter).isNotNull();
      assertThat(filter.acceptValue(valueType))
          .as("record filter should accept value type based on configuration")
          .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = RecordType.class,
        names = {"NULL_VAL", "SBE_UNKNOWN"},
        mode = Mode.EXCLUDE)
    void shouldRejectDisabledRecordType(final RecordType recordType) {
      // given
      TestSupport.setIndexingForRecordType(config.index, recordType, false);

      // when
      exporter.configure(context);

      // then
      final RecordFilter filter = context.getRecordFilter();
      assertThat(filter).isNotNull();
      assertThat(filter.acceptType(recordType))
          .as("record filter should reject record type based on configuration")
          .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = RecordType.class,
        names = {"NULL_VAL", "SBE_UNKNOWN"},
        mode = Mode.EXCLUDE)
    void shouldAcceptEnabledRecordType(final RecordType recordType) {
      // given
      TestSupport.setIndexingForRecordType(config.index, recordType, true);

      // when
      exporter.configure(context);

      // then
      final RecordFilter filter = context.getRecordFilter();
      assertThat(filter).isNotNull();
      assertThat(filter.acceptType(recordType))
          .as("record filter should accept record type based on configuration")
          .isTrue();
    }
  }

  @Nested
  final class TemplatesTest {

    @Test
    void shouldCreateComponentTemplate() {
      // given
      config.index.createTemplate = true;
      exporter.configure(context);
      exporter.open(controller);

      // when
      final var recordMock = mock(Record.class);
      when(recordMock.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
      when(recordMock.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());
      exporter.export(recordMock);

      // then
      verify(client).putComponentTemplate();
    }

    @Test
    void shouldNotCreateComponentTemplate() {
      // given
      config.index.createTemplate = false;
      exporter.configure(context);
      exporter.open(controller);

      // when
      final var recordMock = mock(Record.class);
      when(recordMock.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
      when(recordMock.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());
      exporter.export(recordMock);

      // then
      verify(client, never()).putComponentTemplate();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
    void shouldPutValueTypeTemplate(final ValueType valueType) {
      // given
      config.index.createTemplate = true;
      config.setIncludeEnabledRecords(true);
      TestSupport.setIndexingForValueType(config.index, valueType, true);
      exporter.configure(context);
      exporter.open(controller);

      // when
      final var recordMock = mock(Record.class);
      when(recordMock.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
      when(recordMock.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());
      exporter.export(recordMock);

      // then
      verify(client, times(1)).putIndexTemplate(valueType, VersionUtil.getVersionLowerCase());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
    void shouldNotPutValueTypeTemplate(final ValueType valueType) {
      // given
      config.index.createTemplate = true;
      TestSupport.setIndexingForValueType(config.index, valueType, false);
      exporter.configure(context);
      exporter.open(controller);

      // when
      final var recordMock = mock(Record.class);
      when(recordMock.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
      when(recordMock.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());
      exporter.export(recordMock);

      // then
      verify(client, never()).putIndexTemplate(valueType);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
    void shouldCreateAllTemplatesOnPreviousVersion(final ValueType valueType) {
      // given
      config.index.createTemplate = true;
      config.setIncludeEnabledRecords(false);
      TestSupport.setIndexingForValueType(config.index, valueType, true);
      exporter.configure(context);
      exporter.open(controller);

      // when
      final var recordMock = mock(Record.class);
      when(recordMock.getValueType()).thenReturn(valueType);
      when(recordMock.getBrokerVersion()).thenReturn(VersionUtil.getPreviousVersion());
      exporter.export(recordMock);

      // then
      verify(client, times(1)).putIndexTemplate(valueType, VersionUtil.getPreviousVersion());
    }
  }

  @Nested
  final class FlushTest {

    @Test
    void shouldFlushWhenClientDecides() {
      // given
      exporter.configure(context);
      exporter.open(controller);
      when(client.shouldFlush()).thenReturn(false, true);

      // when
      final var recordMock = mock(Record.class);
      when(recordMock.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
      when(recordMock.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());

      exporter.export(recordMock);
      exporter.export(recordMock);

      // then
      verify(client, times(1)).flush();
    }

    @Test
    void shouldFlushWhenBulkDelayIsReached() {
      // given
      config.bulk.delay = 10;
      exporter.configure(context);
      exporter.open(controller);

      // when
      controller.runScheduledTasks(Duration.ofSeconds(10));

      // then
      verify(client, times(1)).flush();
    }

    @Test
    void shouldFlushOnClose() {
      // given
      exporter.configure(context);
      exporter.open(controller);

      // when
      exporter.close();

      // then
      verify(client, times(1)).flush();
    }

    @Test
    void shouldUpdateLastExportedPositionOnFlush() {
      // given
      final var record =
          ImmutableRecord.builder()
              .withPosition(10L)
              .withBrokerVersion(VersionUtil.getVersionLowerCase())
              .withValueType(ValueType.PROCESS_INSTANCE)
              .build();
      exporter.configure(context);
      exporter.open(controller);
      when(client.shouldFlush()).thenReturn(true);

      // when
      exporter.export(record);

      // then
      assertThat(controller.getPosition()).isEqualTo(10L);
    }

    @Test
    void shouldNotUpdatePositionWhenSkippingDisabledValueType() {
      // given
      TestSupport.setIndexingForValueType(config.index, ValueType.PROCESS_INSTANCE, false);
      final var record =
          ImmutableRecord.builder()
              .withPosition(10L)
              .withBrokerVersion(VersionUtil.getVersionLowerCase())
              .withValueType(ValueType.PROCESS_INSTANCE)
              .build();
      exporter.configure(context);
      exporter.open(controller);

      // when
      exporter.export(record);

      // then
      assertThat(controller.getPosition()).isEqualTo(-1);
      verify(client, never()).index(any(), any());
      verify(client, never()).flush();
    }

    @Test
    void shouldUpdatePositionWhenSkippingDisabledValueTypeAndFlushingAfterwards() {
      // given
      TestSupport.setIndexingForValueType(config.index, ValueType.PROCESS_INSTANCE, false);
      final var record =
          ImmutableRecord.builder()
              .withPosition(10L)
              .withBrokerVersion(VersionUtil.getVersionLowerCase())
              .withValueType(ValueType.PROCESS_INSTANCE)
              .build();
      exporter.configure(context);
      exporter.open(controller);
      exporter.export(record);

      // when
      when(client.shouldFlush()).thenReturn(true);
      controller.runScheduledTasks(Duration.ofSeconds(10));

      // then
      assertThat(controller.getPosition()).isEqualTo(10L);
    }

    @Test
    void shouldNotUpdatePositionOnFlushErrors() {
      // given
      final var record =
          ImmutableRecord.builder()
              .withBrokerVersion(VersionUtil.getVersionLowerCase())
              .withPosition(10L)
              .withValueType(ValueType.PROCESS_INSTANCE)
              .build();
      exporter.configure(context);
      exporter.open(controller);
      when(client.shouldFlush()).thenReturn(true);

      // when
      doThrow(new ElasticsearchExporterException("failed to flush")).when(client).flush();

      // then
      assertThatCode(() -> exporter.export(record))
          .isInstanceOf(ElasticsearchExporterException.class);
      assertThat(controller.getPosition()).isEqualTo(-1L);
    }
  }

  @Nested
  final class ValidationTest {

    @Test
    void shouldNotAllowUnderscoreInIndexPrefix() {
      // given
      config.index.prefix = "i_am_invalid";

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {-1, 0})
    void shouldForbidNonPositiveNumberOfShards(final int invalidNumberOfShards) {
      // given
      config.index.setNumberOfShards(invalidNumberOfShards);

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"1", "-1", "1ms"})
    void shouldNotAllowInvalidMinimumAge(final String invalidMinAge) {
      // given
      config.retention.setMinimumAge(invalidMinAge);

      // when - then
      assertThatCode(() -> exporter.configure(context))
          .isInstanceOf(ExporterException.class)
          .hasMessageContaining("must match pattern '^[0-9]+[dhms]$'")
          .hasMessageContaining("minimumAge '" + invalidMinAge + "'");
    }

    @Test
    void shouldNotAllowInvalidIndexSuffixDatePattern() {
      // given
      config.index.indexSuffixDatePattern = "l";

      // when - then
      assertThatCode(() -> exporter.configure(context))
          .isInstanceOf(ExporterException.class)
          .hasMessageContaining(
              "Expected a valid date format pattern for the given elasticsearch indexSuffixDatePattern, but 'l' was not.")
          .hasMessageContaining("Examples are: 'yyyy-MM-dd' or 'yyyy-MM-dd_HH'");
    }

    @Test
    void shouldForbidNegativeNumberOfReplicas() {
      // given
      config.index.setNumberOfReplicas(-1);

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }

    @Test
    void shouldForbidNegativePriority() {
      // given
      config.index.setTemplatePriority(-1);

      // when - then
      assertThatCode(() -> exporter.configure(context))
          .isInstanceOf(ExporterException.class)
          .hasMessage("Elasticsearch index template priority must be >= 0. Current value: -1");
    }
  }

  @Nested
  final class RecordSequenceTest {

    private static final int PARTITION_ID = 123;
    private final int position = 1;

    @BeforeEach
    void initExporter() {
      config.setIncludeEnabledRecords(true);
      exporter.configure(context);
      exporter.open(controller);
    }

    @Test
    void shouldIndexRecordWithSequence() {
      // given
      final var record = newRecord(PARTITION_ID, ValueType.PROCESS_INSTANCE);

      // when
      exporter.export(record);

      // then
      final var recordSequenceCaptor = ArgumentCaptor.forClass(RecordSequence.class);
      verify(client).index(any(), recordSequenceCaptor.capture());

      final var value = recordSequenceCaptor.getValue();

      assertThat(value)
          .describedAs("Expect that the record is indexed with a sequence")
          .isNotNull();

      assertThat(value.partitionId())
          .describedAs("Expect that the partition id is equal to the record")
          .isEqualTo(PARTITION_ID);

      assertThat(value.counter()).describedAs("Expect that the counter starts at 1").isEqualTo(1);

      assertThat(value.sequence())
          .describedAs(
              "Expect that the sequence is a combination of the partition id and the counter")
          .isEqualTo(((long) PARTITION_ID << 51) + 1);
    }

    @Test
    void shouldEncodePartitionIdInRecordSequence() {
      // given
      final int partitionId1 = 1;
      final int partitionId2 = 2;
      final int partitionId3 = 3;

      final var records =
          List.of(
              newRecord(partitionId1, ValueType.PROCESS_INSTANCE),
              newRecord(partitionId2, ValueType.PROCESS_INSTANCE),
              newRecord(partitionId3, ValueType.PROCESS_INSTANCE),
              newRecord(partitionId1, ValueType.PROCESS_INSTANCE));

      // when
      records.forEach(exporter::export);

      // then
      final var recordSequenceCaptor = ArgumentCaptor.forClass(RecordSequence.class);
      verify(client, times(records.size())).index(any(), recordSequenceCaptor.capture());

      assertThat(recordSequenceCaptor.getAllValues())
          .extracting(RecordSequence::partitionId)
          .containsExactly(partitionId1, partitionId2, partitionId3, partitionId1);
    }

    @Test
    void shouldIncrementRecordCounterByValueType() {
      // given
      final var records =
          List.of(
              newRecord(PARTITION_ID, ValueType.PROCESS_INSTANCE),
              newRecord(PARTITION_ID, ValueType.PROCESS_INSTANCE),
              newRecord(PARTITION_ID, ValueType.VARIABLE),
              newRecord(PARTITION_ID, ValueType.JOB),
              newRecord(PARTITION_ID, ValueType.PROCESS_INSTANCE),
              newRecord(PARTITION_ID, ValueType.JOB));
      when(client.index(any(), any())).thenReturn(true);

      // when
      records.forEach(exporter::export);

      // then
      final var recordSequenceCaptor = ArgumentCaptor.forClass(RecordSequence.class);
      verify(client, times(records.size())).index(any(), recordSequenceCaptor.capture());

      assertThat(recordSequenceCaptor.getAllValues())
          .extracting(RecordSequence::counter)
          .containsExactly(1L, 2L, 1L, 1L, 3L, 2L);
    }

    @Test
    void shouldNotIncrementCounterOnIndexErrors() {
      // given
      final var record = newRecord(PARTITION_ID, ValueType.PROCESS_INSTANCE);

      // when
      doThrow(new ElasticsearchExporterException("failed to index"))
          .when(client)
          .index(any(), any());

      assertThatCode(() -> exporter.export(record))
          .isInstanceOf(ElasticsearchExporterException.class);

      // retry index successfully
      doReturn(true).when(client).index(any(), any());
      when(client.index(any(), any())).thenReturn(true);
      exporter.export(record);

      // then
      final var recordSequenceCaptor = ArgumentCaptor.forClass(RecordSequence.class);
      verify(client, times(2)).index(any(), recordSequenceCaptor.capture());

      assertThat(recordSequenceCaptor.getAllValues())
          .extracting(RecordSequence::counter)
          .describedAs("Expect that the record counter is the same on retry")
          .containsExactly(1L, 1L);
    }

    @Test
    void shouldStoreRecordCountersOnFlush() {
      // given
      when(client.shouldFlush()).thenReturn(true);
      when(client.index(any(), any())).thenReturn(true);

      final var records =
          List.of(
              newRecord(PARTITION_ID, ValueType.PROCESS_INSTANCE),
              newRecord(PARTITION_ID, ValueType.PROCESS_INSTANCE),
              newRecord(PARTITION_ID, ValueType.VARIABLE));

      // when
      records.forEach(exporter::export);

      // then
      final var expectedMetadataAsJSON =
          "{\"recordCountersByValueType\":{\"PROCESS_INSTANCE\":2,\"VARIABLE\":1}}";
      assertThat(controller.readMetadata())
          .isPresent()
          .map(metadata -> new String(metadata, StandardCharsets.UTF_8))
          .hasValue(expectedMetadataAsJSON);
    }

    @Test
    void shouldRestoreRecordCountersOnOpen() {
      // given
      final var storedMetadataAsJSON =
          "{\"recordCountersByValueType\":{\"PROCESS_INSTANCE\":2,\"VARIABLE\":1}}";
      final var serializedMetadata = storedMetadataAsJSON.getBytes(StandardCharsets.UTF_8);
      controller.updateLastExportedRecordPosition(0L, serializedMetadata);

      exporter.open(controller);

      final var records =
          List.of(
              newRecord(PARTITION_ID, ValueType.PROCESS_INSTANCE),
              newRecord(PARTITION_ID, ValueType.VARIABLE),
              newRecord(PARTITION_ID, ValueType.JOB));
      when(client.index(any(), any())).thenReturn(true);

      // when
      records.forEach(exporter::export);

      // then
      final var recordSequenceCaptor = ArgumentCaptor.forClass(RecordSequence.class);
      verify(client, times(records.size())).index(any(), recordSequenceCaptor.capture());

      assertThat(recordSequenceCaptor.getAllValues())
          .extracting(RecordSequence::counter)
          .containsExactly(3L, 2L, 1L);
    }

    private static Record<?> newRecord(final int partitionId, final ValueType valueType) {
      return ImmutableRecord.builder()
          .withBrokerVersion(VersionUtil.getVersionLowerCase())
          .withPartitionId(partitionId)
          .withValueType(valueType)
          .build();
    }
  }
}
