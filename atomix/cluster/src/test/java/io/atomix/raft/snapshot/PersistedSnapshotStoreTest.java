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

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.snapshot.impl.FileBasedSnapshotStoreFactory;
import io.atomix.utils.time.WallClockTimestamp;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PersistedSnapshotStoreTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private PersistedSnapshotStore persistedSnapshotStore;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory = new FileBasedSnapshotStoreFactory();

    final var partitionName = "1";
    final var root = temporaryFolder.getRoot();

    persistedSnapshotStore = factory.createSnapshotStore(root.toPath(), partitionName);
  }

  @Test
  public void shouldReturnZeroWhenNoSnapshotWasTaken() {
    // given

    // when
    final var currentSnapshotIndex = persistedSnapshotStore.getCurrentSnapshotIndex();

    // then
    assertThat(currentSnapshotIndex).isEqualTo(0);
  }

  @Test
  public void shouldReturnEmptyWhenNoSnapshotWasTaken() {
    // given

    // when
    final var optionalLatestSnapshot = persistedSnapshotStore.getLatestSnapshot();

    // then
    assertThat(optionalLatestSnapshot).isEmpty();
  }

  @Test
  public void shouldReturnFalseOnNonExistingSnapshot() {
    // given

    // when
    final var exists = persistedSnapshotStore.hasSnapshotId("notexisting");

    // then
    assertThat(exists).isFalse();
  }

  @Test
  public void shouldCreateSubFoldersOnCreatingDirBasedStore() {
    // given

    // when + then
    assertThat(
            temporaryFolder
                .getRoot()
                .toPath()
                .resolve(FileBasedSnapshotStoreFactory.SNAPSHOTS_DIRECTORY))
        .exists();
    assertThat(
            temporaryFolder
                .getRoot()
                .toPath()
                .resolve(FileBasedSnapshotStoreFactory.PENDING_DIRECTORY))
        .exists();
  }

  @Test
  public void shouldTakeReceivedSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);

    // when
    final var transientSnapshot = persistedSnapshotStore.newReceivedSnapshot("1-0-123");

    // then
    assertThat(transientSnapshot.index()).isEqualTo(index);
  }
}
