/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;

/** This interface is supposed to replace TypedCommandWriter */
public interface TypedCommandWriter {

  /**
   * Append a new command to the result builder
   *
   * @param intent the intent of the command
   * @param value the record of the command
   * @throws ExceededBatchRecordSizeException if the appended command doesn't fit into the
   *     RecordBatch
   */
  void appendNewCommand(Intent intent, RecordValue value);

  /**
   * Append a follow up command to the result builder
   *
   * @param intent the intent of the command
   * @param value the record of the command
   * @throws ExceededBatchRecordSizeException if the appended command doesn't fit into the
   *     RecordBatch
   */
  void appendFollowUpCommand(long key, Intent intent, RecordValue value);

  /**
   * Append a follow up command to the result builder
   *
   * @param intent the intent of the command
   * @param value the record of the command
   * @param operationReference the operation reference of the command
   * @throws ExceededBatchRecordSizeException if the appended command doesn't fit into the
   *     RecordBatch
   */
  void appendFollowUpCommand(long key, Intent intent, RecordValue value, long operationReference);

  /**
   * @param commandLength the length of the command that will be written
   * @return true if a command of the given length can be written
   */
  boolean canWriteCommandOfLength(final int commandLength);
}
