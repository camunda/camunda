/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class RecordCounterTest {
  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();
  private final ExporterTestContext context =
      new ExporterTestContext().setConfiguration(new ExporterTestConfiguration<>("test", config));
  private final ExporterTestController controller = new ExporterTestController();
  private final OpensearchClient client = mock(OpensearchClient.class);
  private final OpensearchExporter exporter =
      new OpensearchExporter() {
        @Override
        protected OpensearchClient createClient() {
          return client;
        }
      };

  @BeforeEach
  void beforeEach() {
    when(client.putIndexTemplate(any())).thenReturn(true);
    when(client.putComponentTemplate()).thenReturn(true);
  }

  @Test
  void shouldIncrementCounterOnSynchronousFlush() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);
    when(mockRecord.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());

    // when a new record is exported
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    exporter.export(mockRecord);

    // then the record counter should be 1
    final var counters = readMetadata();
    assertThat(counters).describedAs("The counter is stored in the metadata").isNotEmpty();
    assertThat(counters.get(valueType))
        .describedAs("The record counter should be 1 as only exported 1 record")
        .isEqualTo(1);
  }

  @Test
  void shouldNotIncrementCounterOnFailedSynchronousFlush() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);
    when(mockRecord.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());

    // when a new record is exported, but the flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    doThrow(new OpensearchExporterException("failed to flush")).when(client).flush();
    assertThatThrownBy(() -> exporter.export(mockRecord))
        .isInstanceOf(OpensearchExporterException.class);

    // then the record counter should be empty
    final var counters = readMetadata();
    assertThat(counters).describedAs("The counter is not stored in the metadata").isEmpty();
  }

  @Test
  void shouldIncrementCounterOnAsynchronousFlush() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);
    when(mockRecord.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());

    // when a new record is exported, but the flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(false);
    exporter.export(mockRecord);
    // the close() method is used to simulate the asynchronous flush
    exporter.close();

    // then the record counter should be 1
    final var counters = readMetadata();
    assertThat(counters).describedAs("The counter is stored in the metadata").isNotEmpty();
    assertThat(counters.get(valueType))
        .describedAs("The record counter should be 1 as only exported 1 record")
        .isEqualTo(1);
  }

  @Test
  void shouldNotIncrementCounterOnFailedAsynchronousFlush() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);
    when(mockRecord.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());

    // when a new record is exported, but the flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(false);
    exporter.export(mockRecord);
    doThrow(new OpensearchExporterException("failed to flush")).when(client).flush();
    // the close() method is used to simulate the asynchronous flush
    exporter.close();

    // then the record counter should be empty
    final var counters = readMetadata();
    assertThat(counters).describedAs("The counter is not stored in the metadata").isEmpty();
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/24192")
  void shouldIncrementCounterOnSynchronousFlushFailure() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);
    when(mockRecord.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());

    // when a new record is exported, but the synchronous flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    doThrow(new OpensearchExporterException("failed to flush")).when(client).flush();
    assertThatThrownBy(() -> exporter.export(mockRecord))
        .isInstanceOf(OpensearchExporterException.class);

    // and the exported record is flushed asynchronously
    doNothing().when(client).flush();
    // the close() method is used to simulate the asynchronous flush
    exporter.close();

    // then the record counter should be 1
    final var counters = readMetadata();
    assertThat(counters).describedAs("The counter is stored in the metadata").isNotEmpty();
    assertThat(counters.get(valueType))
        .describedAs(
            "The record counter should be 1, as we have exported the record asynchronously")
        .isEqualTo(1);
  }

  @Test
  void shouldNotIncrementSequenceOnDuplicateExportedRecord() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);
    when(mockRecord.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());

    // when a new record is exported, but the synchronous flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    doThrow(new OpensearchExporterException("failed to flush")).when(client).flush();
    assertThatThrownBy(() -> exporter.export(mockRecord))
        .isInstanceOf(OpensearchExporterException.class);

    // and the record export is retried
    // when the exporter tries to index the same record multiple times in the same batch,
    // the method returns false as it only keeps a single copy of the record in the batch.
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(false);
    doNothing().when(client).flush();
    exporter.export(mockRecord);

    // then the record counter should be 1
    final var counters = readMetadata();
    assertThat(counters).describedAs("The counter is stored in the metadata").isNotEmpty();
    assertThat(counters.get(valueType))
        .describedAs("The record counter should be 1, as we have exported the same record twice")
        .isEqualTo(1);
  }

  @Test
  void shouldIncrementSequenceOnDifferentExportedRecords() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord1 = mock(Record.class);
    final Record mockRecord2 = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord1.getValueType()).thenReturn(valueType);
    when(mockRecord2.getValueType()).thenReturn(valueType);
    when(mockRecord1.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());
    when(mockRecord2.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());

    // when a new record is exported
    when(client.index(eq(mockRecord1), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    exporter.export(mockRecord1);

    // and another new record export is exported
    when(client.index(eq(mockRecord2), any(RecordSequence.class))).thenReturn(true);
    exporter.export(mockRecord2);

    // then the record counter should be 2
    final var counters = readMetadata();
    assertThat(counters).describedAs("The counter is stored in the metadata").isNotEmpty();
    assertThat(counters.get(valueType))
        .describedAs("The record counter should be 2, as we have exported the two records")
        .isEqualTo(2);
  }

  @Test
  void shouldIncrementCounterOnFlushErrors() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    when(mockRecord.getBrokerVersion()).thenReturn(VersionUtil.getVersionLowerCase());
    when(client.shouldFlush()).thenReturn(true);

    // when
    doThrow(new OpensearchExporterException("failed to flush")).when(client).flush();
    assertThatThrownBy(() -> exporter.export(mockRecord))
        .isInstanceOf(OpensearchExporterException.class);

    // then
    final var recordSequenceCaptor = ArgumentCaptor.forClass(RecordSequence.class);
    verify(client, times(1)).index(any(), recordSequenceCaptor.capture());
    assertThat(recordSequenceCaptor.getAllValues())
        .extracting(RecordSequence::counter)
        .describedAs("Expect that the record counter is incremented")
        .containsExactly(1L);
  }

  private Map<ValueType, Long> readMetadata() {
    return controller
        .readMetadata()
        .map(
            bytes -> {
              try {
                return new ObjectMapper().readValue(bytes, OpensearchExporterMetadata.class);
              } catch (final IOException exception) {
                System.out.println("Failed to map metadata: " + exception);
                return null;
              }
            })
        .map(OpensearchExporterMetadata::getRecordCountersByValueType)
        .orElse(Collections.emptyMap());
  }
}
