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
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

final class ElasticsearchExporterFlushTest {
  private Controller controller;
  private Context context;
  private ElasticsearchClient client;
  private ElasticsearchExporter exporter;

  @BeforeEach
  void setup() {
    final var config = new ElasticsearchExporterConfiguration();
    final var configuration = mock(Configuration.class);
    final var context = mock(Context.class);
    when(configuration.instantiate(eq(ElasticsearchExporterConfiguration.class)))
        .thenReturn(config);
    when(context.getConfiguration()).thenReturn(configuration);
    when(context.getLogger()).thenReturn(mock(Logger.class));

    this.context = context;
    controller = mock(Controller.class);
    client = mock(ElasticsearchClient.class);
    exporter =
        new ElasticsearchExporter() {
          @Override
          protected ElasticsearchClient createClient() {
            return client;
          }
        };
  }

  @Test
  void shouldScheduleFlush() {
    // when
    exporter.configure(context);
    exporter.open(controller);

    // then -- a flush task is scheduled
    final var capturedTask = ArgumentCaptor.forClass(Runnable.class);
    verify(controller).scheduleCancellableTask(any(), capturedTask.capture());

    // and -- when run, the task schedules another flush task
    final var task = capturedTask.getValue();
    task.run();
    verify(controller).scheduleCancellableTask(any(), not(eq(task)));
  }

  @Test
  void shouldFlushOnClose() {
    // given
    exporter.configure(context);
    exporter.open(controller);

    // when
    when(client.shouldFlush()).thenReturn(true);
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
    exporter.configure(context);
    exporter.open(controller);

    // when
    when(client.shouldFlush()).thenReturn(true);
    exporter.export(record);

    // then
    verify(controller).updateLastExportedRecordPosition(position);
  }

  @Test
  void shouldNotCatchExceptionDuringFlush() {
    // given
    exporter.configure(context);
    exporter.open(controller);

    // when
    final var exception = new RuntimeException();
    doThrow(exception).when(client).flush();
    when(client.shouldFlush()).thenReturn(true);

    // then
    assertThatThrownBy(() -> exporter.export(mock(Record.class))).isEqualTo(exception);
  }
}
