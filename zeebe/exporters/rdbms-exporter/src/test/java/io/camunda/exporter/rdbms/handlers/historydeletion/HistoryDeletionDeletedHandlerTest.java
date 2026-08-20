/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.historydeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel.HistoryDeletionTypeDbModel;
import io.camunda.db.rdbms.write.service.HistoryDeletionWriter;
import io.camunda.exporter.rdbms.handlers.HistoryDeletionDeletedHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.ImmutableHistoryDeletionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoryDeletionDeletedHandlerTest {

  private static final Integer PARTITION_ID = 1;
  private static final Long BATCH_OPERATION_KEY = 123L;
  private static final Long RESOURCE_KEY = 456L;

  private final ProtocolFactory factory = new ProtocolFactory();

  private HistoryDeletionWriter writer;
  private HistoryDeletionDeletedHandler handler;

  @Captor private ArgumentCaptor<HistoryDeletionDbModel> historyDeletionCaptor;

  private final Record handledRecord =
      factory.generateRecord(
          ValueType.HISTORY_DELETION,
          r ->
              r.withBatchOperationReference(BATCH_OPERATION_KEY)
                  .withValue(
                      ImmutableHistoryDeletionRecordValue.builder()
                          .withResourceKey(RESOURCE_KEY)
                          .withResourceType(HistoryDeletionType.PROCESS_INSTANCE)
                          .build())
                  .withIntent(HistoryDeletionIntent.DELETED)
                  .withPartitionId(PARTITION_ID));

  private final Record unhandledRecord =
      factory.generateRecordWithIntent(ValueType.HISTORY_DELETION, HistoryDeletionIntent.DELETE);

  @BeforeEach
  void setUp() {
    writer = mock(HistoryDeletionWriter.class);
    handler = new HistoryDeletionDeletedHandler(writer);
  }

  @Test
  void shouldHandleHistoryDeletionDeleted() {
    assertThat(handler.canExport(handledRecord)).isTrue();
  }

  @Test
  void shouldNotHandleHistoryDeletionDeleted() {
    assertThat(handler.canExport(unhandledRecord)).isFalse();
  }

  @Test
  void shouldExportHistoryDeletion() {
    handler.export(handledRecord);
    verify(writer).create(historyDeletionCaptor.capture());

    final HistoryDeletionDbModel capturedModel = historyDeletionCaptor.getValue();
    assertThat(capturedModel.resourceKey()).isEqualTo(RESOURCE_KEY);
    assertThat(capturedModel.resourceType()).isEqualTo(HistoryDeletionTypeDbModel.PROCESS_INSTANCE);
    assertThat(capturedModel.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(capturedModel.partitionId()).isEqualTo(PARTITION_ID);
  }
}
