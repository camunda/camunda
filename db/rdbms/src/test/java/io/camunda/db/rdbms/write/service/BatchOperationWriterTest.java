/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemsDto;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import java.time.OffsetDateTime;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class BatchOperationWriterTest {

  private final ExecutionQueue executionQueue = Mockito.mock(ExecutionQueue.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      Mockito.mock(VendorDatabaseProperties.class);
  private final BatchOperationMapper batchOperationMapper =
      Mockito.mock(BatchOperationMapper.class);
  private final BatchOperationWriter batchOperationWriter =
      new BatchOperationWriter(
          null,
          executionQueue,
          batchOperationMapper,
          RdbmsWriterConfig.builder().batchOperationItemInsertBlockSize(10).build(),
          vendorDatabaseProperties);

  @Test
  void testSplitCorrectly() {
    // given
    final var itemsDto =
        new BatchOperationItemsDto(
            "42",
            LongStream.range(0, 25)
                .boxed()
                .map(
                    i ->
                        new BatchOperationItemDbModel(
                            "42", i, i, BatchOperationItemState.ACTIVE, OffsetDateTime.now(), null))
                .toList());

    // when
    batchOperationWriter.insertItems(itemsDto);

    final var payloadCaptor = ArgumentCaptor.forClass(QueueItem.class);
    Mockito.verify(executionQueue, Mockito.times(3)).executeInQueue(payloadCaptor.capture());

    final var capturedItems =
        payloadCaptor.getAllValues().stream()
            .map(q -> (BatchOperationItemsDto) q.parameter())
            .toList();

    assertThat(capturedItems.get(0).items()).hasSize(10);
    assertThat(capturedItems.get(1).items()).hasSize(10);
    assertThat(capturedItems.get(2).items()).hasSize(5);
  }
}
