/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * Writes to the system partition's key-value store. Only the leader can write, so a writer is
 * handed out by {@link SystemPartitionListener#onBecomingLeader(long, SystemKeyValueWriter)} and
 * revoked when that leadership ends.
 */
public interface SystemKeyValueWriter {

  /**
   * Puts the value under the key. The value is copied, so the caller may reuse it. Thread-safe.
   *
   * @return a future completed with the position of the put once it is committed; it fails if the
   *     leadership that handed out this writer ends before
   */
  ActorFuture<Long> put(SystemPartitionKeyType key, BufferWriter value);
}
