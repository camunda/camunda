/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import org.junit.jupiter.api.Test;

class BatchOperationCreatedV3ApplierTest {

  private final MutableBatchOperationState state = mock(MutableBatchOperationState.class);
  private final BatchOperationCreatedV3Applier applier = new BatchOperationCreatedV3Applier(state);

  @Test
  void shouldCreateBatchOperation() {
    // given
    final var record =
        new BatchOperationCreationRecord().setBatchOperationKey(42L).setStorageOrdinal(1234);

    // when
    applier.applyState(42L, record);

    // then
    verify(state).create(42L, record);
  }
}
