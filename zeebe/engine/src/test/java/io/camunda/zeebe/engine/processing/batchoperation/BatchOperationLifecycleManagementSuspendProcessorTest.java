/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.mockito.Mockito.*;

import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** This test class only covers cases not covered by the LifecycleBatchOperationTest engine test */
class BatchOperationLifecycleManagementSuspendProcessorTest {

  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private TypedRejectionWriter rejectionWriter;
  private TypedResponseWriter responseWriter;
  private BatchOperationLifecycleManagementSuspendProcessor processor;
  private KeyGenerator keyGenerator;
  private BatchOperationState batchOperationState;

  @BeforeEach
  void setUp() {
    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    rejectionWriter = mock(TypedRejectionWriter.class);
    responseWriter = mock(TypedResponseWriter.class);
    keyGenerator = mock(KeyGenerator.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);
    when(writers.rejection()).thenReturn(rejectionWriter);
    when(writers.response()).thenReturn(responseWriter);

    batchOperationState = mock(BatchOperationState.class);
    final var state = mock(ProcessingState.class);
    when(state.getBatchOperationState()).thenReturn(batchOperationState);

    final var authCheckBehavior = mock(AuthorizationCheckBehavior.class);
    when(authCheckBehavior.isAuthorizedOrInternalCommand(any(AuthorizationRequest.class)))
        .thenReturn(Either.right(null));

    when(keyGenerator.nextKey()).thenReturn(1L);

    // Inject mocked writers
    processor =
        new BatchOperationLifecycleManagementSuspendProcessor(
            writers,
            null,
            state,
            authCheckBehavior,
            keyGenerator,
            mock(BatchOperationMetrics.class));
  }

  @Test
  void shouldRejectWhenInInvalidState() {
    // given
    final long pauseKey = 123L;
    final long batchOperationKey = 456L;
    final BatchOperationLifecycleManagementRecord recordValue =
        new BatchOperationLifecycleManagementRecord();
    recordValue.setBatchOperationKey(batchOperationKey);

    final PersistedBatchOperation batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getKey()).thenReturn(batchOperationKey);
    when(batchOperation.getStatus()).thenReturn(BatchOperationStatus.FAILED);
    when(batchOperation.canSuspend()).thenReturn(false);

    when(batchOperationState.get(batchOperationKey)).thenReturn(Optional.of(batchOperation));

    // when
    final var record = new MockTypedRecord<>(pauseKey, new RecordMetadata(), recordValue);
    processor.processNewCommand(record);

    // then
    verify(rejectionWriter)
        .appendRejection(eq(record), eq(RejectionType.INVALID_STATE), anyString());
  }
}
