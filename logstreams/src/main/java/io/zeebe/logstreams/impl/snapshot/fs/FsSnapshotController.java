/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.logstreams.impl.snapshot.fs;

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.*;
import io.zeebe.logstreams.state.StateSnapshotMetadata;
import java.util.function.Predicate;
import org.slf4j.Logger;

public class FsSnapshotController implements SnapshotController {
  private static final Logger LOG = Loggers.FS_SNAPSHOT_LOGGER;

  private final SnapshotStorage storage;
  private final SnapshotSupport resource;
  private final String name;

  public FsSnapshotController(
      final SnapshotStorage storage, final String name, final SnapshotSupport resource) {
    this.storage = storage;
    this.resource = resource;
    this.name = name;
  }

  @Override
  public void takeSnapshot(StateSnapshotMetadata metadata, long commitPosition) throws Exception {
    if (metadata.getLastWrittenEventPosition() > commitPosition) {
      return;
    }

    takeSnapshot(metadata);
  }

  @Override
  public void takeSnapshot(final StateSnapshotMetadata metadata) throws Exception {
    if (storage.snapshotExists(name, metadata.getLastSuccessfulProcessedEventPosition())) {
      return;
    }

    final SnapshotWriter writer =
        storage.createSnapshot(name, metadata.getLastSuccessfulProcessedEventPosition());

    try {
      writer.writeSnapshot(resource);
      writer.commit();
    } catch (final Exception ex) {
      writer.abort();
      throw ex;
    }
  }

  @Override
  public StateSnapshotMetadata recover(
      long commitPosition, int term, Predicate<StateSnapshotMetadata> filter) throws Exception {
    StateSnapshotMetadata metadata = StateSnapshotMetadata.createInitial(term);

    final ReadableSnapshot lastSnapshot = storage.getLastSnapshot(name);
    if (lastSnapshot != null) {
      final StateSnapshotMetadata recovered =
          new StateSnapshotMetadata(
              lastSnapshot.getPosition(), lastSnapshot.getPosition(), term, true);

      lastSnapshot.recoverFromSnapshot(resource);
      metadata = recovered;
    }

    return metadata;
  }

  @Override
  public void purgeAll(final Predicate<StateSnapshotMetadata> filter) throws Exception {
    storage
        .listSnapshots()
        .stream()
        .filter(s -> s.getName().equals(name) && filter.test(convertMetadata(s)))
        .forEach(this::purge);
  }

  private void purge(io.zeebe.logstreams.spi.SnapshotMetadata metadata) {
    storage.purgeSnapshot(metadata.getName());
  }

  private StateSnapshotMetadata convertMetadata(io.zeebe.logstreams.spi.SnapshotMetadata metadata) {
    return new StateSnapshotMetadata(metadata.getPosition(), metadata.getPosition(), -1, true);
  }
}
