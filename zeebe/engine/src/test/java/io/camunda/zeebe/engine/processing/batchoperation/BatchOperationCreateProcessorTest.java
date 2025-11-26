/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class BatchOperationCreateProcessorTest {

  @Test
  void test() {
    final var list = new ArrayList<>(1000_000);
    final var init =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(123L)
            .setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
            .setEntityFilter(BufferUtil.wrapString(""))
            .setMigrationPlan(
                new io.camunda.zeebe.protocol.impl.record.value.batchoperation
                        .BatchOperationProcessInstanceMigrationPlan()
                    .setTargetProcessDefinitionKey(456L))
            .setPartitionIds(List.of(1));

    final var start = System.nanoTime();
    for (int i = 0; i < 1000_000; i++) {
      final var batchOperationCreationRecord =
          new BatchOperationCreationRecord()
              .setBatchOperationKey(123L)
              .setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
              .setEntityFilter(BufferUtil.wrapString(""))
              .setMigrationPlan(
                  new io.camunda.zeebe.protocol.impl.record.value.batchoperation
                          .BatchOperationProcessInstanceMigrationPlan()
                      .setTargetProcessDefinitionKey(456L))
              .setPartitionIds(List.of(1));
      //      batchOperationCreationRecord.wrap(init);
      list.add(batchOperationCreationRecord);
    }
    final var end = System.nanoTime();
    final long duration = end - start;

    System.out.println(list.size()); // to avoid optimization

    // print duration in nano and miliseconds
    System.out.println("Duration: " + duration + " ns");
    System.out.println("Duration: " + duration / 1_000_000 + " ms");
  }
}
