/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft;

/**
 * Listener which will be invoked when a new snapshot is received by this follower from a leader.
 * When a new snapshot is received via raft replication, the log will be reset and all entries in
 * the log is deleted and creates a new empty log. Hence any consumers of the log may have to reset
 * its internal state so that it can start with a new log and a new snapshot.
 *
 * <p>The difference between this listener and {@link
 * io.camunda.zeebe.snapshots.PersistedSnapshotListener} is that {@link
 * io.camunda.zeebe.snapshots.PersistedSnapshotListener} notifies when a snapshot is taken locally
 * and when a snapshot is received via replication. This listener only notifies when a snapshot is
 * received via raft replication, which happens only when the follower's log is lagging behind the
 * leader.
 *
 * <p>These listeners are invoked in the Raft thread. Hence it should not do any heavy computations.
 * Any time consuming steps should be delegated to another thread/actor.
 */
public interface SnapshotReplicationListener {

  /**
   * Will be called when the snapshot receiving has started. This should close the consumers of the
   * log.
   */
  void onSnapshotReplicationStarted();

  /**
   * Will be called after the snapshot replication is completed. The snapshot replication can //
   * complete either a new snapshot is committed or the snapshot replication is aborted. // If a new
   * snapshot has been committed, the log will be empty.
   *
   * @param term the current term
   */
  void onSnapshotReplicationCompleted(long term);
}
