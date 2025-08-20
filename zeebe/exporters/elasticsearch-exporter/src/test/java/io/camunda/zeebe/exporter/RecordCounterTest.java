/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

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
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class RecordCounterTest {
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
  void shouldIncrementCounterOnSynchronousFlush() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);

    // when a new record is exported
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    exporter.export(mockRecord);

    // then the record counter should be 1
    final var counters = readMetadata();
    assertThat(counters)
        .describedAs("The counter is stored in the metadata")
        .isNotEmpty()
        .describedAs("The record counter should be 1 as only exported 1 record")
        .containsEntry(valueType, 1L);
  }

  @Test
  void shouldNotIncrementCounterOnFailedSynchronousFlush() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);

    // when a new record is exported, but the flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    doThrow(new ElasticsearchExporterException("failed to flush")).when(client).flush();
    assertThatThrownBy(() -> exporter.export(mockRecord))
        .isInstanceOf(ElasticsearchExporterException.class);

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

    // when a new record is exported, but the flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(false);
    exporter.export(mockRecord);
    // the close() method is used to simulate the asynchronous flush
    exporter.close();

    // then the record counter should be 1
    final var counters = readMetadata();
    assertThat(counters)
        .describedAs("The counter is stored in the metadata")
        .isNotEmpty()
        .describedAs("The record counter should be 1 as only exported 1 record")
        .containsEntry(valueType, 1L);
  }

  @Test
  void shouldNotIncrementCounterOnFailedAsynchronousFlush() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);

    // when a new record is exported, but the flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(false);
    exporter.export(mockRecord);
    doThrow(new ElasticsearchExporterException("failed to flush")).when(client).flush();
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

    // when a new record is exported, but the synchronous flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    doThrow(new ElasticsearchExporterException("failed to flush")).when(client).flush();
    assertThatThrownBy(() -> exporter.export(mockRecord))
        .isInstanceOf(ElasticsearchExporterException.class);

    // and the exported record is flushed asynchronously
    doNothing().when(client).flush();
    // the close() method is used to simulate the asynchronous flush
    exporter.close();

    // then the record counter should be 1
    final var counters = readMetadata();
    assertThat(counters)
        .describedAs("The counter is stored in the metadata")
        .isNotEmpty()
        .describedAs(
            "The record counter should be 1, as we have exported the record asynchronously")
        .containsEntry(valueType, 1L);
  }

  @Test
  void shouldNotIncrementSequenceOnDuplicateExportedRecord() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    final var valueType = ValueType.PROCESS_INSTANCE;
    when(mockRecord.getValueType()).thenReturn(valueType);

    // when a new record is exported, but the synchronous flush fails
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    doThrow(new ElasticsearchExporterException("failed to flush")).when(client).flush();
    assertThatThrownBy(() -> exporter.export(mockRecord))
        .isInstanceOf(ElasticsearchExporterException.class);

    // and the record export is retried
    // when the exporter tries to index the same record multiple times in the same batch,
    // the method returns false as it only keeps a single copy of the record in the batch.
    when(client.index(eq(mockRecord), any(RecordSequence.class))).thenReturn(false);
    doNothing().when(client).flush();
    exporter.export(mockRecord);

    // then the record counter should be 1
    final var counters = readMetadata();
    assertThat(counters)
        .describedAs("The counter is stored in the metadata")
        .isNotEmpty()
        .describedAs("The record counter should be 1, as we have exported the same record twice")
        .containsEntry(valueType, 1L);
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

    // when a new record is exported
    when(client.index(eq(mockRecord1), any(RecordSequence.class))).thenReturn(true);
    when(client.shouldFlush()).thenReturn(true);
    exporter.export(mockRecord1);

    // and another new record export is exported
    when(client.index(eq(mockRecord2), any(RecordSequence.class))).thenReturn(true);
    exporter.export(mockRecord2);

    // then the record counter should be 2
    final var counters = readMetadata();
    assertThat(counters)
        .describedAs("The counter is stored in the metadata")
        .isNotEmpty()
        .describedAs("The record counter should be 2, as we have exported the two records")
        .containsEntry(valueType, 2L);
  }

  @Test
  void shouldIncrementCounterOnFlushErrors() {
    // given
    exporter.configure(context);
    exporter.open(controller);
    final Record mockRecord = mock(Record.class);
    when(client.shouldFlush()).thenReturn(true);

    // when
    doThrow(new ElasticsearchExporterException("failed to flush")).when(client).flush();
    assertThatThrownBy(() -> exporter.export(mockRecord))
        .isInstanceOf(ElasticsearchExporterException.class);

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
                return new ObjectMapper().readValue(bytes, ElasticsearchExporterMetadata.class);
              } catch (final IOException exception) {
                System.out.println("Failed to map metadata: " + exception);
                return null;
              }
            })
        .map(ElasticsearchExporterMetadata::getRecordCountersByValueType)
        .orElse(Collections.emptyMap());
  }
}
