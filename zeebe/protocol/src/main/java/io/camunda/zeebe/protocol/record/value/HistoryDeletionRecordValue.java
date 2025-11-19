/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableHistoryDeletionRecordValue.Builder.class)
public interface HistoryDeletionRecordValue extends RecordValue {

  /**
   * The key of the resource to delete. Depending on the {@link HistoryDeletionType} this can be one
   * of four things:
   *
   * <ul>
   *   <li>{@link HistoryDeletionType#PROCESS_INSTANCE}: the process instance key
   *   <li>{@link HistoryDeletionType#PROCESS_DEFINITION}: the process definition key
   *   <li>{@link HistoryDeletionType#DECISION_INSTANCE}: the decision instance key
   *   <li>{@link HistoryDeletionType#DECISION_DEFINITION}: the decision definition key
   * </ul>
   *
   * @return the key of the resource
   */
  long getResourceKey();

  /** Returns the type of resource to delete. */
  HistoryDeletionType getResourceType();

  /** Returns the key of the batch operation that triggered this history deletion * */
  long getBatchOperationKey();
}
