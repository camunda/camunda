/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class BatchOperationStateTest {

  private MutableProcessingState processingState;
  private MutableBatchOperationState state;

  @BeforeEach
  public void setup() {
    state = processingState.getBatchOperationState();
  }

  @Test
  void shouldCreateBatchOperation() throws JsonProcessingException {
    // given
    final var batchOperationKey = 1L;
    final var type = BatchOperationType.PROCESS_CANCELLATION;
    final var filter =
        new ProcessInstanceFilter.Builder()
            .processDefinitionIds("process")
            .processDefinitionVersions(1)
            .build();
    final var record =
        new BatchOperationCreationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setBatchOperationType(type)
            .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)));

    // when
    state.create(batchOperationKey, record);

    // then
    final var batchOperation = state.get(batchOperationKey).get();
    final var recordFilter =
        new ObjectMapper().readValue(batchOperation.getEntityFilter(), ProcessInstanceFilter.class);

    assertThat(batchOperation.getKey()).isEqualTo(batchOperationKey);
    assertThat(batchOperation.getBatchOperationType()).isEqualTo(type);
    assertThat(recordFilter).isEqualTo(filter);
    assertThat(batchOperation.getStatus()).isEqualTo(BatchOperationStatus.CREATED);
  }
}
