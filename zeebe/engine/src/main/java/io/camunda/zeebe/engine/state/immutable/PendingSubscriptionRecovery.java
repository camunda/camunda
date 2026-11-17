/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

/**
 * Rebuilds the transient pending subscriptions from the persisted subscriptions, in chunks. Meant
 * for the pending subscription recovery only: a scan reuses the state's key and value instances, so
 * calls must not run concurrently with each other.
 */
public interface PendingSubscriptionRecovery {

  /** Marks both the start of a scan and its end; no element instance has this key. */
  long NO_ELEMENT_INSTANCE = -1L;

  /**
   * Adds the pending subscriptions of one chunk to the transient state. A chunk only ends between
   * element instances, once {@code batchLimit} subscriptions were visited. So the limit is soft: an
   * element instance with more subscriptions than the limit is still visited in one chunk, and
   * every chunk makes progress.
   *
   * @return the element instance key to resume from, or {@link #NO_ELEMENT_INSTANCE} when done
   */
  long recoverPending(long fromElementInstanceKey, int batchLimit);
}
