/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AzureEventHubExporterTest {

  private AzureEventHubExporterConfiguration config;
  private ExporterTestContext context;
  private ExporterTestController controller;
  private EventHubProducerClient producerClient;
  private AzureEventHubExporter exporter;

  @BeforeEach
  void setUp() {
    config = new AzureEventHubExporterConfiguration()
        .setConnectionString("Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=dGVzdA==")
        .setEventHubName("test-hub")
        .setMaxBatchSize(2);

    context = new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>("test", config));

    controller = new ExporterTestController();
    producerClient = mock(EventHubProducerClient.class);

    exporter = new AzureEventHubExporter() {
      @Override
      protected EventHubProducerClient createProducerClient() {
        return producerClient;
      }
    };
  }

  @Test
  void shouldHandleMultipleBatchesCorrectly() {
    // given
    exporter.configure(context);
    exporter.open(controller);

    // Create mock batches with different capacities
    final EventDataBatch batch1 = mock(EventDataBatch.class);
    final EventDataBatch batch2 = mock(EventDataBatch.class);
    final EventDataBatch batch3 = mock(EventDataBatch.class);

    // Track which batch we're on
    final AtomicInteger batchCounter = new AtomicInteger(0);
    
    when(producerClient.createBatch()).thenAnswer(invocation -> {
      int count = batchCounter.getAndIncrement();
      return switch (count) {
        case 0 -> batch1;
        case 1 -> batch2;
        case 2 -> batch3;
        default -> mock(EventDataBatch.class);
      };
    });

    // Simulate batch1 accepting 2 events then becoming full
    final AtomicInteger batch1Count = new AtomicInteger(0);
    when(batch1.tryAdd(any(EventData.class))).thenAnswer(invocation -> {
      int count = batch1Count.incrementAndGet();
      return count <= 2; // Accept first 2, reject 3rd
    });
    when(batch1.getCount()).thenAnswer(invocation -> batch1Count.get());

    // Simulate batch2 accepting 2 events then becoming full
    final AtomicInteger batch2Count = new AtomicInteger(0);
    when(batch2.tryAdd(any(EventData.class))).thenAnswer(invocation -> {
      int count = batch2Count.incrementAndGet();
      return count <= 2; // Accept first 2, reject 3rd
    });
    when(batch2.getCount()).thenAnswer(invocation -> batch2Count.get());

    // Simulate batch3 accepting 1 event
    final AtomicInteger batch3Count = new AtomicInteger(0);
    when(batch3.tryAdd(any(EventData.class))).thenAnswer(invocation -> {
      batch3Count.incrementAndGet();
      return true;
    });
    when(batch3.getCount()).thenAnswer(invocation -> batch3Count.get());

    // Create 5 mock records
    for (int i = 0; i < 5; i++) {
      final Record<?> record = createMockRecord(i);
      exporter.export(record);
    }

    // when - trigger flush
    exporter.close();

    // then - verify 3 batches were sent
    verify(producerClient, times(3)).send(any(EventDataBatch.class));
    
    // Verify batch1 was sent with 2 events
    verify(producerClient).send(batch1);
    assertThat(batch1Count.get()).isEqualTo(2);
    
    // Verify batch2 was sent with 2 events
    verify(producerClient).send(batch2);
    assertThat(batch2Count.get()).isEqualTo(2);
    
    // Verify batch3 was sent with 1 event
    verify(producerClient).send(batch3);
    assertThat(batch3Count.get()).isEqualTo(1);

    // Verify position was updated
    verify(controller).updateLastExportedRecordPosition(4);
  }

  @Test
  void shouldSkipEventTooLargeForBatch() {
    // given
    exporter.configure(context);
    exporter.open(controller);

    final EventDataBatch batch = mock(EventDataBatch.class);
    when(producerClient.createBatch()).thenReturn(batch);
    
    // Simulate batch always rejecting (event too large)
    when(batch.tryAdd(any(EventData.class))).thenReturn(false);
    when(batch.getCount()).thenReturn(0);

    // when
    final Record<?> record = createMockRecord(1);
    exporter.export(record);
    exporter.close();

    // then - should not send any batch (event was skipped)
    verify(producerClient, times(0)).send(any(EventDataBatch.class));
    
    // But position should still be updated
    verify(controller).updateLastExportedRecordPosition(1);
  }

  private Record<?> createMockRecord(final long position) {
    final Record<?> record = mock(Record.class);
    when(record.getPosition()).thenReturn(position);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.toJson()).thenReturn("{\"position\":" + position + "}");
    return record;
  }
}
