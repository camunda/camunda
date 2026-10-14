/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

/**
 * Notified of the role transitions of this broker's system partition replica, like a {@link
 * io.camunda.zeebe.broker.PartitionListener} for data partitions. Called on the system partition's
 * actor thread, so implementations must not block.
 */
public interface SystemPartitionListener {

  /**
   * This broker became the leader and has applied every entry committed before, so its {@link
   * SystemKeyValueReader reader} is up to date. The writer is valid until the next transition,
   * after which it rejects puts.
   */
  default void onBecomingLeader(final long term, final SystemKeyValueWriter writer) {}

  default void onBecomingFollower(final long term) {}

  default void onBecomingInactive(final long term) {}
}
