/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.impl.backpressure;

public interface AppendLimiter {

  /**
   * Try to add to the in flight appends. If success, {@link #onCommit(long)} ()} must be called
   * when the appending was successful
   *
   * @param position the corresponding position
   * @return true if request is added to the in flight requests, false otherwise
   */
  boolean tryAcquire(Long position);

  /**
   * Notify when then entry with the given position have been committed. This will release the
   * acquired position and decrement the in flight count.
   *
   * @param position the committed position
   */
  void onCommit(long position);

  /**
   * The current in flight append request count.
   *
   * @return the current in flight
   */
  int getInflight();

  /**
   * The current limit of concurrent appends.
   *
   * @return the limit
   */
  int getLimit();
}
