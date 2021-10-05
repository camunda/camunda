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

import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.nio.file.Path;
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
  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return Optional.ofNullable(currentPersistedSnapshot.get());
  }

  @Override
  public ActorFuture<Void> purgePendingSnapshots() {
    receivedSnapshots.clear();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Boolean> addSnapshotListener(final PersistedSnapshotListener listener) {
    listeners.add(listener);
    return null;
  }

  @Override
  public ActorFuture<Boolean> removeSnapshotListener(final PersistedSnapshotListener listener) {
    listeners.remove(listener);
    return null;
  }

  @Override
  public long getCurrentSnapshotIndex() {
    if (currentPersistedSnapshot.get() == null) {
      return 0;
    }
    return currentPersistedSnapshot.get().getIndex();
  }

  @Override
  public ActorFuture<Void> delete() {
    currentPersistedSnapshot.set(null);
    receivedSnapshots.clear();
    return null;
  }

  @Override
  public Path getPath() {
    return null;
  }

  @Override
  public ActorFuture<Void> copySnapshot(
      final PersistedSnapshot snapshot, final Path targetDirectory) {
    return null;
  }

  @Override
  public ReceivedSnapshot newReceivedSnapshot(final String snapshotId) {
    final var newSnapshot = new InMemorySnapshot(this, snapshotId);
    receivedSnapshots.add(newSnapshot);
    return newSnapshot;
  }

  @Override
  public void close() {}

  public void newSnapshot(final InMemorySnapshot persistedSnapshot) {
    currentPersistedSnapshot.set(persistedSnapshot);
    listeners.forEach(l -> l.onNewSnapshot(persistedSnapshot));
  }
}
