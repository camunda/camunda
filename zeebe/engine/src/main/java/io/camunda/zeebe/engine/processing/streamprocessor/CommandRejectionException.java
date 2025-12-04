/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.record.RejectionType;

/**
 * Exception thrown when a command should be rejected. This triggers automatic rollback of the
 * current RocksDB transaction, discard of buffered follow-up records and side effects, and writing
 * of a rejection record.
 *
 * <p>This is a stackless exception for performance reasons. Stack traces are not useful for
 * expected command rejections as all diagnostic information is contained in the rejection reason.
 * The cost of generating stack traces can be significant in high-rejection workloads.
 *
 * <p>This exception is caught by the stream processing state machine, which ensures that: - The
 * RocksDB transaction is rolled back - Buffered follow-up events are discarded - Side effects are
 * discarded - A rejection record is written to the log - A rejection response is sent to the client
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * public void processRecord(TypedRecord<MyRecord> command) {
 *   final var record = command.getValue();
 *
 *   if (!validator.isValid(record)) {
 *     throw new CommandRejectionException(
 *       RejectionType.INVALID_STATE,
 *       "Expected valid record but validation failed"
 *     );
 *   }
 *
 *   // State modifications only happen if validation passes
 *   state.updateRecord(record);
 * }
 * }</pre>
 *
 * @see io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.ForbiddenException
 */
public class CommandRejectionException extends RuntimeException {

  private final RejectionType rejectionType;

  /**
   * Creates a new command rejection exception.
   *
   * @param rejectionType the type of rejection
   * @param reason the human-readable rejection reason, which will be included in the rejection
   *     record and response
   */
  public CommandRejectionException(final RejectionType rejectionType, final String reason) {
    super(reason);
    this.rejectionType = rejectionType;
  }

  /**
   * Returns the rejection type for this command rejection.
   *
   * @return the rejection type
   */
  public RejectionType getRejectionType() {
    return rejectionType;
  }

  /**
   * Overridden to make this exception stackless. Stack traces are not useful for expected command
   * rejections and can be expensive to generate in high-throughput scenarios.
   *
   * @return this exception instance
   */
  @Override
  public Throwable fillInStackTrace() {
    // Stack traces aren't useful for expected command rejections.
    // All diagnostic information is contained in the rejection reason.
    return this;
  }
}
