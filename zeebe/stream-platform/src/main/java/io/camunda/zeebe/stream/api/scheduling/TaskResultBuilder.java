/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.scheduling;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import java.util.List;

/** Here the interface is just a suggestion. Can be whatever PDT team thinks is best to work with */
public interface TaskResultBuilder {

  long NULL_KEY = -1;

  /**
   * Appends a record to the result without a key
   *
   * @return returns true if the record still fits into the result, false otherwise
   */
  default boolean appendCommandRecord(final Intent intent, final UnifiedRecordValue value) {
    return appendCommandRecord(NULL_KEY, intent, value);
  }

  /**
   * Appends a record to the result
   *
   * @return returns true if the record still fits into the result, false otherwise
   */
  default boolean appendCommandRecord(
      final long key, final Intent intent, final UnifiedRecordValue value) {
    return appendCommandRecord(key, intent, value, FollowUpCommandMetadata.empty());
  }

  /**
   * Appends a record to the result
   *
   * @return returns true if the record still fits into the result, false otherwise
   */
  boolean appendCommandRecord(
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final FollowUpCommandMetadata metadata);

  /**
   * Checks if a whole list of records can be added to the result builder.
   *
   * @return returns <code>true</code> if the whole list of records still fit into the result,
   *     <code>false</code> otherwise
   */
  boolean canAppendRecords(
      final List<? extends UnifiedRecordValue> value, final FollowUpCommandMetadata metadata);

  TaskResult build();
}
