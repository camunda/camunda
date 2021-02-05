/*
 * Copyright 2018-present Open Networking Foundation
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
 * limitations under the License.
 */
package io.atomix.core;

import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.snapshots.raft.PersistedSnapshotListener;
import io.zeebe.snapshots.raft.ReceivableSnapshotStore;
import io.zeebe.snapshots.raft.ReceivedSnapshot;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Optional;

class NoopSnapshotStore implements ReceivableSnapshotStore {

  @Override
  public boolean hasSnapshotId(final String id) {
    return false;
  }

  @Override
  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return Optional.empty();
  }

  @Override
  public ActorFuture<Void> purgePendingSnapshots() {
    return null;
  }

  @Override
  public ActorFuture<Boolean> addSnapshotListener(final PersistedSnapshotListener listener) {
    return null;
  }

  @Override
  public ActorFuture<Boolean> removeSnapshotListener(final PersistedSnapshotListener listener) {
    return null;
  }

  @Override
  public long getCurrentSnapshotIndex() {
    return 0;
  }

  @Override
  public ActorFuture<Void> delete() {
    return null;
  }

  @Override
  public ReceivedSnapshot newReceivedSnapshot(final String snapshotId) {
    return null;
  }

  @Override
  public void close() {}
}
