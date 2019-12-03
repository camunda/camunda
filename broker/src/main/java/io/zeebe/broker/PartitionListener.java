package io.zeebe.broker;

import io.zeebe.logstreams.log.LogStream;

public interface PartitionListener {

  default void onBecomingFollower(
      final int partitionId, final int term, final LogStream logStream) {
    onBecomingFollower(partitionId, logStream);
  }

  default void onBecomingFollower(final int partitionId, final LogStream logStream) {
    onBecomingFollower(partitionId);
  }

  default void onBecomingFollower(final int partitionId) {}

  default void onBecomingLeader(final int partitionId, final int term, final LogStream logStream) {
    onBecomingLeader(partitionId, logStream);
  }

  default void onBecomingLeader(final int partitionId, final LogStream logStream) {
    onBecomingLeader(partitionId);
  }

  default void onBecomingLeader(final int partitionId) {}
}
