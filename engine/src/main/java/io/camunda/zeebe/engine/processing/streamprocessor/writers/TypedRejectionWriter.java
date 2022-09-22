/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.streamplatform.api.TypedRecord;
import io.camunda.zeebe.streamplatform.api.records.RecordBatch;

public interface TypedRejectionWriter {

  /**
   * Append a rejection command to the result builder
   *
   * @param command the command that is rejected
   * @param type the type of rejection
   * @param reason the reason for the rejection
   * @throws RecordBatch.ExceededBatchRecordSizeException if the appended command doesn't fit into
   *     the RecordBatch
   */
  void appendRejection(
      TypedRecord<? extends RecordValue> command, RejectionType type, String reason);
}
