/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import java.util.Set;

public interface MutableBatchOperationState extends BatchOperationState {

  /**
   * Stores the batch operation in the state.
   *
   * @param record the batch operation creation record to create
   */
  void create(final long batchOperationKey, final BatchOperationCreationRecord record);

  void appendItemKeys(final long batchOperationKey, final Set<Long> itemKeys);

  void removeItemKeys(final long batchOperationKey, final Set<Long> itemKeys);

  void complete(final long batchOperationKey);
}
