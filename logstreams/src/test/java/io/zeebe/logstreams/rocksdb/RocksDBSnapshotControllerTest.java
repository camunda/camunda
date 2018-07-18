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
package io.zeebe.logstreams.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.logstreams.state.SnapshotMetadata;
import io.zeebe.logstreams.util.RocksDBWrapper;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.RocksDBException;

public class RocksDBSnapshotControllerTest {
  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private static final Predicate<SnapshotMetadata> TRUISM = (s) -> true;

  private RocksDBStorage storage;
  private RocksDBController controller;
  private RocksDBSnapshotController snapshotController;

  @Before
  public void setup() throws IOException {
    final File snapshotsDirectory = tempFolderRule.newFolder("snapshots");
    final File runtimeDirectory = tempFolderRule.newFolder("runtime");
    storage = new RocksDBStorage(runtimeDirectory, snapshotsDirectory);

    controller = new RocksDBController();
    autoCloseableRule.manage(controller);

    snapshotController = new RocksDBSnapshotController(controller, storage);
  }

  @Test
  public void shouldThrowExceptionOnTakeSnapshotIfClosed() throws Exception {
    // given
    final SnapshotMetadata metadata = new SnapshotMetadata(1, 1, 1, false);

    // then
    assertThat(controller.isOpened()).isFalse();
    assertThatThrownBy(() -> snapshotController.takeSnapshot(metadata))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldFailToTakeSnapshotOnPreExistingLocation() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();
    final SnapshotMetadata metadata = new SnapshotMetadata(1, 1, 1, false);

    // when
    controller.open(dbDir, false);
    snapshotController.takeSnapshot(metadata);

    // then
    assertThatThrownBy(() -> snapshotController.takeSnapshot(metadata))
        .isInstanceOf(RocksDBException.class);
  }

  @Test
  public void shouldTakeSnapshot() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();
    final String key = "test";
    final int value = 3;
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    final SnapshotMetadata initial = new SnapshotMetadata(0, 0, 0, false);
    final SnapshotMetadata metadata = new SnapshotMetadata(1, 1, 1, false);

    // when
    wrapper.wrap(controller.open(dbDir, false));
    wrapper.putInt(key, value);
    snapshotController.takeSnapshot(metadata);
    controller.close();
    wrapper.wrap(controller.open(storage.getSnapshotDirectoryFor(metadata), true));

    // then
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldOpenNewDatabaseIfNoSnapshotsToRecoverFrom() throws Exception {
    // given
    final int term = 2;
    final long commitPosition = 3L;
    final SnapshotMetadata expected = SnapshotMetadata.createInitial(term);

    // when
    final SnapshotMetadata recovered = snapshotController.recover(commitPosition, term, TRUISM);

    // then
    assertThat(recovered).isEqualTo(expected);
  }

  @Test
  public void shouldOpenNewDatabaseIfNoValidSnapshotsToRecoverFrom() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();
    final int term = 2;
    final long commitPosition = 3L;
    final SnapshotMetadata initial = new SnapshotMetadata(1L, commitPosition + 1, term, false);
    final SnapshotMetadata expected = SnapshotMetadata.createInitial(term);

    // when
    controller.open(dbDir, false);
    snapshotController.takeSnapshot(initial);
    controller.close();

    // when
    final SnapshotMetadata recovered = snapshotController.recover(commitPosition, term, TRUISM);

    // then
    assertThat(recovered).isEqualTo(expected);
    assertThat(storage.getSnapshotDirectoryFor(initial)).exists();
  }

  @Test
  public void shouldRemovePreExistingNewDatabaseOnRecoverIfNoSnapshotsToRecoverFrom()
      throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();
    final int term = 0;
    final String key = "test";
    final int value = 1;
    final SnapshotMetadata expected = SnapshotMetadata.createInitial(term);
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(controller.open(dbDir, false));
    wrapper.putInt(key, value);
    controller.close();
    final SnapshotMetadata recovered = snapshotController.recover(3L, term, TRUISM);
    wrapper.wrap(controller.getDb());

    // then
    assertThat(recovered).isEqualTo(expected);
    assertThat(wrapper.mayExist(key)).isFalse();
  }

  @Test
  public void shouldRecoverFromSnapshotsOnlyIfPredicateAcceptsIt() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();
    final String key = "test";
    final int value = 1;
    final SnapshotMetadata good = new SnapshotMetadata(1, 1, 1, false);
    final SnapshotMetadata bad =
        new SnapshotMetadata(
            good.getLastSuccessfulProcessedEventPosition() + 1,
            good.getLastWrittenEventPosition() + 1,
            good.getLastWrittenEventTerm(),
            false);
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(controller.open(dbDir, false));
    wrapper.putInt(key, value - 1);
    snapshotController.takeSnapshot(bad);
    wrapper.putInt(key, value);
    snapshotController.takeSnapshot(good);
    controller.close();

    // then
    assertThat(storage.listRecoverable(bad.getLastWrittenEventPosition())).contains(bad, good);

    // when
    final SnapshotMetadata recovered =
        snapshotController.recover(
            bad.getLastWrittenEventPosition(),
            good.getLastWrittenEventTerm(),
            (s) -> s.equals(good));
    wrapper.wrap(controller.getDb());

    // then
    assertThat(recovered).isEqualTo(good);
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldPurgeAllOtherSnapshots() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();
    final String key = "test";
    final SnapshotMetadata[] others =
        new SnapshotMetadata[] {
          new SnapshotMetadata(1, 2, 0, false),
          new SnapshotMetadata(3, 4, 0, false),
          new SnapshotMetadata(7, 8, 0, false)
        };
    final SnapshotMetadata expected = new SnapshotMetadata(5, 6, 0, false);
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(controller.open(dbDir, false));
    wrapper.putInt(key, 2);
    snapshotController.takeSnapshot(others[1]);
    wrapper.putInt(key, 3);
    snapshotController.takeSnapshot(others[2]);
    wrapper.putInt(key, 4);
    snapshotController.takeSnapshot(expected);
    wrapper.putInt(key, 1);
    controller.close();
    snapshotController.purgeAllExcept(expected);

    // then
    assertThat(storage.list()).hasSize(1);

    // when
    final SnapshotMetadata recovered =
        snapshotController.recover(
            expected.getLastWrittenEventPosition(), expected.getLastWrittenEventTerm(), TRUISM);
    wrapper.wrap(controller.getDb());

    // then
    assertThat(recovered).isEqualTo(expected);
    assertThat(wrapper.getInt(key)).isEqualTo(4);
  }
}
