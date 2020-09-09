/*
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
package io.atomix.raft.snapshot;

import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.snapshots.raft.PersistedSnapshotListener;
import io.zeebe.snapshots.raft.ReceivableSnapshotStore;
import io.zeebe.snapshots.raft.ReceivedSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class TestSnapshotStore implements ReceivableSnapshotStore {

  final AtomicReference<InMemorySnapshot> currentPersistedSnapshot;
  final List<InMemorySnapshot> receivedSnapshots = new CopyOnWriteArrayList<>();
  final List<PersistedSnapshotListener> listeners = new CopyOnWriteArrayList<>();

  public TestSnapshotStore(final AtomicReference<InMemorySnapshot> persistedSnapshotRef) {
    currentPersistedSnapshot = persistedSnapshotRef;
  }

  @Override
  public boolean hasSnapshotId(final String id) {
    return currentPersistedSnapshot.get() != null
        && currentPersistedSnapshot.get().getId().equals(id);
  }

  @Override
  public ReceivedSnapshot newReceivedSnapshot(final String snapshotId) {
    final var newSnapshot = new InMemorySnapshot(this, snapshotId);
    receivedSnapshots.add(newSnapshot);
    return newSnapshot;
  }

  @Override
  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return Optional.ofNullable(currentPersistedSnapshot.get());
  }

  @Override
  public void purgePendingSnapshots() throws IOException {
    receivedSnapshots.clear();
  }

  @Override
  public void addSnapshotListener(final PersistedSnapshotListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeSnapshotListener(final PersistedSnapshotListener listener) {
    listeners.remove(listener);
  }

  @Override
  public long getCurrentSnapshotIndex() {
    if (currentPersistedSnapshot.get() == null) {
      return 0;
    }
    return currentPersistedSnapshot.get().getIndex();
  }

  @Override
  public void delete() {
    currentPersistedSnapshot.set(null);
    receivedSnapshots.clear();
  }

  @Override
  public void close() {}

  public void newSnapshot(final InMemorySnapshot persistedSnapshot) {
    currentPersistedSnapshot.set(persistedSnapshot);
    listeners.forEach(l -> l.onNewSnapshot(persistedSnapshot));
  }
}
