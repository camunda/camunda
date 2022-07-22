/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import org.rocksdb.RocksDBException;

/** Builder to compose the processing result */
public interface ProcessingResultBuilder {

  /**
   * Appends a record to the result
   *
   * @return returns itself for method chaining
   */
  ProcessingResultBuilder appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final RecordValue value);

  /**
   * Sets the response for the result; will be overwritten if called more than once
   *
   * @return returns itself for method chaining
   */
  ProcessingResultBuilder withResponse(
      final RecordType type,
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final ValueType valueType,
      final RejectionType rejectionType,
      final String rejectionReason,
      final long requestId,
      final int requestStreamId);

  /**
   * Appends a task to be executed after a successful commit ProcessingResultBuilder (replacement
   * for side effects)
   *
   * @return returns itself for method chaining
   */
  ProcessingResultBuilder appendPostCommitTask(PostCommitTask task);

  /**
   * Resets the processing result build to its initial states (removes all follow-up records, the
   * response, post-commit tasks and rolls back the transaction).
   *
   * @return returns itself for method chaining
   */
  ProcessingResultBuilder reset() throws RocksDBException, Exception;

  /**
   * Resets itself with the post commit tasks reset
   *
   * @return itself for method chaining
   */
  ProcessingResultBuilder resetPostCommitTasks();

  ProcessingResult build();

  boolean canWriteEventOfLength(int eventLength);

  int getMaxEventLength();
}
