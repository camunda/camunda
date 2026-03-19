/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstream;

import java.util.concurrent.CompletableFuture;

/**
 * Notified when at least one new record becomes available on a LogStream partition, or when the
 * broker-side long-poll ceiling elapses.
 *
 * <p>Implementations must be safe to complete from any thread: the RAFT commit thread calls {@link
 * #onRecordAvailable()} outside the actor context.
 */
public interface LogRecordAwaiter {

  /**
   * Returns a future that completes when records are available at or after the awaited position, or
   * when the given ceiling elapses.
   *
   * @param timeoutMs maximum wait in milliseconds; must be positive
   * @return a future that completes (with {@code null}) when records are available or when the
   *     timeout fires
   */
  CompletableFuture<Void> awaitRecord(long timeoutMs);

  /**
   * Immediately completes the pending future (if any), waking the parked poll actor. May be called
   * from any thread.
   */
  void onRecordAvailable();
}
