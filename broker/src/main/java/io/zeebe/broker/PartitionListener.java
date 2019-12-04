/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import io.zeebe.logstreams.log.LogStream;

public interface PartitionListener {

  default void onBecomingFollower(
      final int partitionId, final long term, final LogStream logStream) {
    onBecomingFollower(partitionId, logStream);
  }

  default void onBecomingFollower(final int partitionId, final LogStream logStream) {
    onBecomingFollower(partitionId);
  }

  default void onBecomingFollower(final int partitionId) {}

  default void onBecomingLeader(final int partitionId, final long term, final LogStream logStream) {
    onBecomingLeader(partitionId, logStream);
  }

  default void onBecomingLeader(final int partitionId, final LogStream logStream) {
    onBecomingLeader(partitionId);
  }

  default void onBecomingLeader(final int partitionId) {}
}
