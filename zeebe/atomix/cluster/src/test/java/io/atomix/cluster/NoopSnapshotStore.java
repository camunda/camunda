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
package io.atomix.cluster;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

public class NoopSnapshotStore implements ReceivableSnapshotStore {

  @Override
  public boolean hasSnapshotId(final String id) {
    return false;
  }

  @Override
  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return Optional.empty();
  }

  @Override
  public ActorFuture<Set<PersistedSnapshot>> getAvailableSnapshots() {
    return null;
  }

  @Override
  public ActorFuture<Long> getCompactionBound() {
    return null;
  }

  @Override
  public ActorFuture<Void> abortPendingSnapshots() {
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
  public Path getPath() {
    return null;
  }

  @Override
  public ActorFuture<ReceivedSnapshot> newReceivedSnapshot(final String snapshotId) {
    return null;
  }

  @Override
  public void close() {}

  @Override
  public Optional<PersistedSnapshot> getBootstrapSnapshot() {
    return Optional.empty();
  }

  @Override
  public ActorFuture<PersistedSnapshot> copyForBootstrap(
      final PersistedSnapshot persistedSnapshot, final BiConsumer<Path, Path> copySnapshot) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> deleteBootstrapSnapshots() {
    return CompletableActorFuture.completed();
  }
}
