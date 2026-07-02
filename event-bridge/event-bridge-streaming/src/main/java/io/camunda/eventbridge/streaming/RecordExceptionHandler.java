/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * Decides what the {@link StreamRuntime} does when deserializing or processing a <em>single</em>
 * record throws — the analogue of Kafka Streams' deserialization/processing exception handlers.
 *
 * <p>The default, {@link #FAIL_FAST}, stops the runtime so a restart reprocesses from the last
 * committed offset: a poison record or a processing bug is surfaced, never silently skipped. A
 * handler may instead return {@link Decision#SKIP} to log the record and advance past it, keeping
 * the stream flowing (at-least-once minus the skipped record).
 */
@FunctionalInterface
public interface RecordExceptionHandler {

  /** What to do with a record whose deserialize/process step threw. */
  enum Decision {
    /**
     * Stop the runtime; on restart it reprocesses from the last committed offset (no data loss).
     */
    FAIL,
    /** Log and skip this record, advancing past it so it is committed and never retried. */
    SKIP
  }

  Decision onError(int partition, long offset, Throwable error);

  /** Fail fast on any record error — the safe default. */
  RecordExceptionHandler FAIL_FAST = (partition, offset, error) -> Decision.FAIL;
}
