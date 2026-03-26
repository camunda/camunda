/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.service;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.EventBridgeBatchIterator;
import org.agrona.concurrent.UnsafeBuffer;

public class EventBridgeBatchValidator {


  static ValidationResult validate(final byte[] body) {
    if (body == null || body.length < EventBridgeBatch.HEADER_LENGTH) {
      return ValidationResult.failure("Request body too short");
    }

    final var buffer = new UnsafeBuffer(body);
    final var entryCount = EventBridgeBatch.getEntryCount(buffer, 0);

    if (entryCount <= 0) {
      return ValidationResult.failure("Entry count must be positive, got " + entryCount);
    }

    final var entriesLength = body.length - EventBridgeBatch.ENTRIES_OFFSET;

    final var walkedCount = EventBridgeBatchIterator.validateEntries(
        buffer, EventBridgeBatch.ENTRIES_OFFSET, entriesLength);

    if (walkedCount < 0) {
      return ValidationResult.failure("Malformed entry data");
    }

    if (walkedCount != entryCount) {
      return ValidationResult.failure(
          "Entry count mismatch: header says " + entryCount + " but found " + walkedCount);
    }

    return ValidationResult.success(entryCount);
  }

  record ValidationResult(boolean valid, int entryCount, String error) {

    static ValidationResult success(final int entryCount) {
      return new ValidationResult(true, entryCount, null);
    }

    static ValidationResult failure(final String error) {
      return new ValidationResult(false, 0, error);
    }
  }

}
