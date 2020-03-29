/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.raft.storage.snapshot.impl;

import io.atomix.primitive.service.PrimitiveService;
import io.atomix.raft.storage.log.RaftLog;

/**
 * Support for periodically persisting {@link PrimitiveService} snapshots to disk.
 *
 * <p>To store a state machine's state, simply implement the {@link #snapshot(SnapshotWriter)}
 * method and write the complete state machine state to the snapshot via the {@link SnapshotWriter}.
 * Raft will periodically invoke the method to take a new snapshot of the state machine's state when
 * the underlying log rotates segments.
 *
 * <p>
 *
 * <pre>{@code
 * public class MyStateMachine extends StateMachine implements Snapshottable {
 *   private long counter;
 *
 *   public void snapshot(SnapshotWriter writer) {
 *     writer.writeLong(counter);
 *   }
 *
 *   public long increment(Commit<Increment> commit) {
 *     counter++;
 *   }
 * }
 *
 * }</pre>
 *
 * Snapshot writing is only one component of snapshotting. Snapshottable state machines must also be
 * able to recover from snapshots stored on disk after a failure. When a server recovers from an
 * existing Raft log, the log will be replayed to the state machine, and when a point in logical
 * time (an {@code index}) at which a snapshot was taken and persisted is reached, that snapshot
 * will be applied to the state machine via the {@link #install(SnapshotReader)} method.
 *
 * <p>
 *
 * <pre>{@code
 * public class MyStateMachine extends StateMachine implements Snapshottable {
 *   private long counter;
 *
 *   public void install(SnapshotReader reader) {
 *     counter = reader.readLong();
 *   }
 * }
 *
 * }</pre>
 *
 * Implementations of the {@link #install(SnapshotReader)} method should always read precisely what
 * implementations of {@link #snapshot(SnapshotWriter)} write. State machines can potentially use a
 * mixture of {@code SNAPSHOT} and other commands, and state machine implementations should take
 * care not to overwrite non-snapshot command state with snapshots. For simpler state machines,
 * <em>users should use either snapshotting or log cleaning but not both</em>.
 */
public interface Snapshottable {

  /**
   * Takes a snapshot of the state machine state.
   *
   * <p>This method will be called each time the underlying {@link RaftLog Log} rotates segments.
   * Once the snapshot has been written, the snapshot will be stored on disk and eventually
   * completed. Note that snapshots are normally not immediately completed upon completion of this
   * method as servers must wait for certain conditions to be met before persisting a snapshot.
   * Therefore, state machines should not assume that once a snapshot has been written that the
   * state machine can or will recover from that snapshot. Snapshot writers should also ensure that
   * all snapshottable state machine state is written on each snapshot. Typically, only the most
   * recent snapshot is applied to a state machine upon recovery, so no assumptions should be made
   * about the persistence or retention of older snapshots.
   *
   * @param writer The snapshot writer.
   */
  void snapshot(SnapshotWriter writer);

  /**
   * Installs a snapshot of the state machine state.
   *
   * <p>This method will be called while a server is replaying its log at startup. Typically, only
   * the most recent snapshot of the state machine state will be installed upon log replay. State
   * machines should recover all snapshottable state machine state from an installed snapshot.
   *
   * @param reader The snapshot reader.
   */
  void install(SnapshotReader reader);
}
