/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
final class ElasticsearchExporterFlushTest {
  @Mock private Controller controller;
  @Mock private ElasticsearchClient client;
  @Mock private Context context;
  private ElasticsearchExporter exporter;

  @BeforeEach
  void setup() {
    final var esConfig = new ElasticsearchExporterConfiguration();
    final var exporterConfig = mock(Configuration.class);
    when(exporterConfig.instantiate(eq(ElasticsearchExporterConfiguration.class)))
        .thenReturn(esConfig);
    when(context.getConfiguration()).thenReturn(exporterConfig);
    when(context.getLogger()).thenReturn(mock(Logger.class));

    exporter =
        new ElasticsearchExporter() {
          @Override
          protected ElasticsearchClient createClient() {
            return client;
          }
        };

    exporter.configure(context);
    exporter.open(controller);
  }

  @Test
  void shouldScheduleFlush() {
    // given -- a flush task should be scheduled after opening
    final var capturedTask = ArgumentCaptor.forClass(Runnable.class);
    verify(controller).scheduleCancellableTask(any(), capturedTask.capture());

    // then -- when run, the task schedules another flush task
    final var task = capturedTask.getValue();
    task.run();
    verify(controller).scheduleCancellableTask(any(), not(eq(task)));
  }

  @Test
  void shouldFlushOnClose() {
    // when
    exporter.close();

    // then
    verify(client).flush();
  }

  @Test
  void shouldUpdateLastPositionOnFlush() {
    // given
    final long position = 10;
    final var record = mock(Record.class);
    when(record.getPosition()).thenReturn(position);
    when(client.shouldFlush()).thenReturn(true);

    // when
    exporter.export(record);

    // then
    verify(controller).updateLastExportedRecordPosition(position);
  }

  @Test
  void shouldNotCatchExceptionDuringFlush() {
    // given
    final var exception = new RuntimeException();

    // when
    when(client.shouldFlush()).thenReturn(true);
    doThrow(exception).when(client).flush();

    // then
    assertThatThrownBy(() -> exporter.export(mock(Record.class))).isEqualTo(exception);
  }
}
