/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api.records;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * Represents a modifiable batch of record, which means we can add multiple Records to the batch.
 * For further processing the user can iterate of the appended entries and retrieve the needed data.
 */
public interface MutableRecordBatch extends ImmutableRecordBatch {

  /**
   * Allows to add a new Record to the batch
   *
   * @param key the key of the record
   * @param sourceIndex the position/index in the current batch which caused that entry; should be
   *     set to -1 if no entry caused it
   * @param recordType the type of the record, part of the record metadata, must be set
   * @param intent the intent of the record, part of the record metadata, must be set
   * @param rejectionType the rejection type, part of the record metadata, can be set to a
   *     NULL_VALUE
   * @param rejectionReason the rejection reason, part of the record metadata, can be empty
   * @param valueType the value type, part of the record metadata, must be set
   * @param valueWriter the actual record value
   */
  void appendRecord(
      final long key,
      final int sourceIndex,
      final RecordType recordType,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final ValueType valueType,
      final BufferWriter valueWriter);
}
