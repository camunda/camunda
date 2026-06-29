/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.exporter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.eventbridge.zeebe.exporter.ZeebeRecordExporter.ExporterConfiguration;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

final class ZeebeRecordExporterTest {

  private final EventBridgeClient client = mock(EventBridgeClient.class);
  private final BatchPublisher batch = mock(BatchPublisher.class);
  private final Controller controller = mock(Controller.class);

  @Test
  void shouldPublishBatchAndAdvancePositionWhenBatchSizeReached() {
    // given — an exporter flushing every 2 records, with a mocked client
    final ZeebeRecordExporter exporter = openExporter(2);

    // when — exporting one record stays below the batch size
    exporter.export(jobRecord(3, 100L));

    // then — nothing published yet
    verify(batch, never()).publishToTopic(anyString(), anyInt());
    verify(controller, never()).updateLastExportedRecordPosition(anyInt());

    // when — the second record reaches the batch size
    exporter.export(jobRecord(3, 200L));

    // then — the batch is published to the record's partition and the position is advanced
    verify(batch).publishToTopic("zeebe-records", 3);
    verify(controller).updateLastExportedRecordPosition(200L);
  }

  @Test
  void shouldFlushRemainderOnClose() {
    // given
    final ZeebeRecordExporter exporter = openExporter(100);
    exporter.export(jobRecord(3, 100L));

    // when
    exporter.close();

    // then — the partial batch is flushed and its last position acknowledged
    verify(batch).publishToTopic("zeebe-records", 3);
    verify(controller).updateLastExportedRecordPosition(100L);
  }

  private ZeebeRecordExporter openExporter(final int batchSize) {
    when(client.newBatch()).thenReturn(batch);
    when(batch.add(anyString(), any(byte[].class))).thenReturn(batch);
    when(batch.publishToTopic(anyString(), anyInt()))
        .thenReturn(CompletableFuture.completedFuture(List.of(1L, 2L)));

    final ExporterConfiguration config = new ExporterConfiguration();
    config.topic = "zeebe-records";
    config.batchSize = batchSize;
    config.flushIntervalMs = 0; // no scheduled flush in the test

    final Configuration configuration = mock(Configuration.class);
    when(configuration.instantiate(ExporterConfiguration.class)).thenReturn(config);
    final Context context = mock(Context.class);
    when(context.getConfiguration()).thenReturn(configuration);
    when(context.getLogger()).thenReturn(LoggerFactory.getLogger(ZeebeRecordExporterTest.class));

    final ZeebeRecordExporter exporter = new ZeebeRecordExporter(url -> client);
    exporter.configure(context);
    exporter.open(controller);
    return exporter;
  }

  private static Record<?> jobRecord(final int partitionId, final long position) {
    final JobRecord value = new JobRecord().setType("payment");
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    return new CopiedRecord<>(value, metadata, 99L, partitionId, position, -1L, 1L);
  }
}
