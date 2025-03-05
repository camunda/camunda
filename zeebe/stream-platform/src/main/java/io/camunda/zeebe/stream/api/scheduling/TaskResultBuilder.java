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

/** Here the interface is just a suggestion. Can be whatever PDT team thinks is best to work with */
public interface TaskResultBuilder {

  long NULL_KEY = -1;

  /**
   * Appends a record to the result
   *
   * @return returns true if the record still fits into the result, false otherwise
   */
  boolean appendCommandRecord(final long key, final Intent intent, final UnifiedRecordValue value);

  /**
   * Appends a record to the result
   *
   * @return returns true if the record still fits into the result, false otherwise
   */
  boolean appendCommandRecord(final long key, final Intent intent, final UnifiedRecordValue value, final long operationReference);


  /**
   * Appends a record to the result without a key
   *
   * @return returns true if the record still fits into the result, false otherwise
   */
  default boolean appendCommandRecord(final Intent intent, final UnifiedRecordValue value) {
    return appendCommandRecord(NULL_KEY, intent, value);
  }

  TaskResult build();
}
