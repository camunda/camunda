/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * A single-threaded unit of stream processing the {@link StreamRuntime} drives for one source
 * partition. The runtime calls {@link #init()} once after durable state is restored, {@link
 * #process(Object)} per record, {@link #flush()} then {@link #checkpoint()} at the commit barrier,
 * and {@link #close()} on shutdown.
 *
 * <p>There is exactly one Task per source partition and the runtime drives it from a single thread,
 * so a Task never needs synchronization and is guaranteed per-partition single-writer semantics.
 * The runtime owns offsets and durability; a Task owns only its processing state.
 *
 * @param <R> the decoded record type the task consumes
 */
public interface Task<R> {

  /** Processes one source record (in per-partition offset order). */
  void process(R record);

  /** Called once after durable state has been restored, before any {@link #process}. */
  default void init() {}

  /** Emit buffered/produced output so latency stays bounded (called before {@link #checkpoint}). */
  default void flush() {}

  /** Make working state durable; invoked inside the runtime's checkpoint transaction. */
  default void checkpoint() {}

  /** Called once on shutdown — release resources. */
  default void close() {}
}
