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

/**
 * Validates an {@link EventBridgeBatch} at the gateway level before forwarding to the broker.
 * Catches malformed requests early — before any flow control, queue slot, or Raft bandwidth is
 * consumed.
 *
 * <p>Validation order (cheapest first):
 *
 * <ol>
 *   <li>Minimum size check (header must fit)
 *   <li>Version check (must be supported)
 *   <li>Batch length consistency (header value vs actual body size)
 *   <li>Entry count positivity
 *   <li>CRC-32C validation (producer-computed, covers attributes → end of batch)
 *   <li>Entry structure walk (length-prefixed entries must fit within the batch)
 *   <li>Entry count consistency (header count vs walked count)
 * </ol>
 */
public final class EventBridgeBatchValidator {

  private EventBridgeBatchValidator() {}

  /**
   * Validates a raw batch byte array.
   *
   * @param body the complete batch bytes (header + entries) as received from the client
   * @return validation result with entry count on success, or error message on failure
   */
  public static ValidationResult validate(final byte[] body) {
    if (body == null || body.length < EventBridgeBatch.HEADER_LENGTH) {
      return ValidationResult.failure("Request body too short");
    }

    final var buffer = new UnsafeBuffer(body);

    // Version check — reject unknown formats before parsing further
    final int version = EventBridgeBatch.getVersion(buffer, 0);
    if (version != EventBridgeBatch.VERSION_1) {
      return ValidationResult.failure(
          "Unsupported batch version: " + version + ", expected: " + EventBridgeBatch.VERSION_1);
    }

    // Batch length consistency — header value must match actual body size
    final int batchLength = EventBridgeBatch.getBatchLength(buffer, 0);
    final int expectedTotalSize = EventBridgeBatch.totalSize(batchLength);
    if (expectedTotalSize != body.length) {
      return ValidationResult.failure(
          "Batch length mismatch: header says "
              + expectedTotalSize
              + " bytes total, but body is "
              + body.length
              + " bytes");
    }

    // Entry count positivity
    final int entryCount = EventBridgeBatch.getEntryCount(buffer, 0);
    if (entryCount <= 0) {
      return ValidationResult.failure("Entry count must be positive, got " + entryCount);
    }

    // CRC validation — covers attributes, entryCount, reserved, and all entry bytes.
    // Catches wire corruption before the broker wastes resources on a bad batch.
    if (!EventBridgeBatch.validateCrc(buffer, 0)) {
      return ValidationResult.failure("CRC mismatch — batch data corrupted in transit");
    }

    // Entry structure walk — verify each entry's length prefix is valid
    final int entriesLength = body.length - EventBridgeBatch.HEADER_LENGTH;
    final int walkedCount =
        EventBridgeBatchIterator.validateEntries(
            buffer, EventBridgeBatch.HEADER_LENGTH, entriesLength);

    if (walkedCount < 0) {
      return ValidationResult.failure("Malformed entry data");
    }

    // Entry count consistency — header count must match actual entries
    if (walkedCount != entryCount) {
      return ValidationResult.failure(
          "Entry count mismatch: header says " + entryCount + " but found " + walkedCount);
    }

    return ValidationResult.success(entryCount);
  }

  /**
   * Result of batch validation.
   *
   * @param valid true if the batch is well-formed
   * @param entryCount number of entries in the batch (0 on failure)
   * @param error human-readable error message (null on success)
   */
  public record ValidationResult(boolean valid, int entryCount, String error) {

    public static ValidationResult success(final int entryCount) {
      return new ValidationResult(true, entryCount, null);
    }

    public static ValidationResult failure(final String error) {
      return new ValidationResult(false, 0, error);
    }
  }
}
