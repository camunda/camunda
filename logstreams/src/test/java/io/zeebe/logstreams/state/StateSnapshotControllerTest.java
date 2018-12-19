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
package io.zeebe.logstreams.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.util.RocksDBWrapper;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StateSnapshotControllerTest {
  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private static final Predicate<StateSnapshotMetadata> TRUISM = (s) -> true;

  private StateStorage storage;
  private StateSnapshotController snapshotController;

  @Before
  public void setup() throws IOException {
    final File snapshotsDirectory = tempFolderRule.newFolder("snapshots");
    final File runtimeDirectory = tempFolderRule.newFolder("runtime");
    storage = new StateStorage(runtimeDirectory, snapshotsDirectory);

    snapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), storage);

    autoCloseableRule.manage(snapshotController);
  }

  @Test
  public void shouldThrowExceptionOnTakeSnapshotIfClosed() throws Exception {
    // given
    final StateSnapshotMetadata metadata = new StateSnapshotMetadata(1, 1, 1, false);

    // then
    assertThat(snapshotController.isDbOpened()).isFalse();
    assertThatThrownBy(() -> snapshotController.takeSnapshot(metadata))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldNotTakeSnapshotOnPreExistingLocation() throws Exception {
    // given
    final StateSnapshotMetadata metadata = new StateSnapshotMetadata(1, 1, 1, false);

    // when
    snapshotController.openDb();
    snapshotController.takeSnapshot(metadata);
    snapshotController.takeSnapshot(metadata);

    // then
    assertThat(storage.list()).hasSize(1);
  }

  @Test
  public void shouldTakeSnapshot() throws Exception {
    // given
    final String key = "test";
    final int value = 3;
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    final StateSnapshotMetadata metadata = new StateSnapshotMetadata(1, 1, 1, false);

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, value);
    snapshotController.takeSnapshot(metadata);
    snapshotController.close();
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldOpenNewDatabaseIfNoSnapshotsToRecoverFrom() throws Exception {
    // given
    final int term = 2;
    final long commitPosition = 3L;
    final StateSnapshotMetadata expected = StateSnapshotMetadata.createInitial(term);

    // when
    final StateSnapshotMetadata recovered =
        snapshotController.recover(commitPosition, term, TRUISM);

    // then
    assertThat(recovered).isEqualTo(expected);
  }

  @Test
  public void shouldOpenNewDatabaseIfNoValidSnapshotsToRecoverFrom() throws Exception {
    // given
    final int term = 2;
    final long commitPosition = 3L;
    final StateSnapshotMetadata initial =
        new StateSnapshotMetadata(1L, commitPosition + 1, term, false);
    final StateSnapshotMetadata expected = StateSnapshotMetadata.createInitial(term);

    // when
    snapshotController.openDb();
    snapshotController.takeSnapshot(initial);
    snapshotController.close();

    // when
    final StateSnapshotMetadata recovered =
        snapshotController.recover(commitPosition, term, TRUISM);

    // then
    assertThat(recovered).isEqualTo(expected);
    assertThat(storage.getSnapshotDirectoryFor(initial)).exists();
  }

  @Test
  public void shouldRemovePreExistingNewDatabaseOnRecoverIfNoSnapshotsToRecoverFrom()
      throws Exception {
    // given
    final int term = 0;
    final String key = "test";
    final int value = 1;
    final StateSnapshotMetadata expected = StateSnapshotMetadata.createInitial(term);
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, value);
    snapshotController.close();
    final StateSnapshotMetadata recovered = snapshotController.recover(3L, term, TRUISM);
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(recovered).isEqualTo(expected);
    assertThat(wrapper.mayExist(key)).isFalse();
  }

  @Test
  public void shouldRecoverFromSnapshotsOnlyIfPredicateAcceptsIt() throws Exception {
    // given
    final String key = "test";
    final int value = 1;
    final StateSnapshotMetadata good = new StateSnapshotMetadata(1, 1, 1, false);
    final StateSnapshotMetadata bad =
        new StateSnapshotMetadata(
            good.getLastSuccessfulProcessedEventPosition() + 1,
            good.getLastWrittenEventPosition() + 1,
            good.getLastWrittenEventTerm(),
            false);
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, value - 1);
    snapshotController.takeSnapshot(bad);
    wrapper.putInt(key, value);
    snapshotController.takeSnapshot(good);
    snapshotController.close();

    // then
    assertThat(storage.listRecoverable(bad.getLastWrittenEventPosition())).contains(bad, good);

    // when
    final StateSnapshotMetadata recovered =
        snapshotController.recover(
            bad.getLastWrittenEventPosition(),
            good.getLastWrittenEventTerm(),
            (s) -> s.equals(good));
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(recovered).isEqualTo(good);
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldPurgeAllOtherSnapshots() throws Exception {
    // given
    final String key = "test";
    final StateSnapshotMetadata[] others =
        new StateSnapshotMetadata[] {
          new StateSnapshotMetadata(1, 2, 0, false),
          new StateSnapshotMetadata(3, 4, 0, false),
          new StateSnapshotMetadata(7, 8, 0, false)
        };
    final StateSnapshotMetadata expected = new StateSnapshotMetadata(5, 6, 0, false);
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, 1);
    snapshotController.takeSnapshot(others[0]);
    wrapper.putInt(key, 2);
    snapshotController.takeSnapshot(others[1]);
    wrapper.putInt(key, 3);
    snapshotController.takeSnapshot(others[2]);
    wrapper.putInt(key, 4);
    snapshotController.takeSnapshot(expected);
    wrapper.putInt(key, 5);
    snapshotController.close();
    snapshotController.purgeAllExcept(expected);

    // then
    assertThat(storage.list()).hasSize(1);

    // when
    final StateSnapshotMetadata recovered =
        snapshotController.recover(
            expected.getLastWrittenEventPosition(), expected.getLastWrittenEventTerm(), TRUISM);
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(recovered).isEqualTo(expected);
    assertThat(wrapper.getInt(key)).isEqualTo(4);
  }

  @Test
  public void shouldPurgeAllMatching() throws Exception {
    // given
    final String key = "test";
    final StateSnapshotMetadata[] snapshots =
        new StateSnapshotMetadata[] {
          new StateSnapshotMetadata(1, 2, 0, false), new StateSnapshotMetadata(3, 4, 0, false)
        };
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, 1);
    snapshotController.takeSnapshot(snapshots[0]);
    wrapper.putInt(key, 2);
    snapshotController.takeSnapshot(snapshots[1]);
    wrapper.putInt(key, 3);
    snapshotController.close();
    snapshotController.purgeAll(s -> s.getLastSuccessfulProcessedEventPosition() == 3);

    // then
    assertThat(storage.list()).hasSize(1);

    // when
    final StateSnapshotMetadata recovered =
        snapshotController.recover(
            snapshots[1].getLastWrittenEventPosition(),
            snapshots[1].getLastWrittenEventTerm(),
            TRUISM);
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(recovered).isEqualTo(snapshots[0]);
    assertThat(wrapper.getInt(key)).isEqualTo(1);
  }

  @Test
  public void shouldPurgeAllSnapshots() throws Exception {
    // given
    final String key = "test";
    final StateSnapshotMetadata[] snapshots =
        new StateSnapshotMetadata[] {
          new StateSnapshotMetadata(1, 2, 0, false), new StateSnapshotMetadata(3, 4, 0, false)
        };
    final RocksDBWrapper wrapper = new RocksDBWrapper();

    // when
    wrapper.wrap(snapshotController.openDb());
    wrapper.putInt(key, 1);
    snapshotController.takeSnapshot(snapshots[0]);
    wrapper.putInt(key, 2);
    snapshotController.takeSnapshot(snapshots[1]);
    wrapper.putInt(key, 3);
    snapshotController.close();
    snapshotController.purgeAll();

    // then
    assertThat(storage.list()).isEmpty();

    // when
    final StateSnapshotMetadata recovered =
        snapshotController.recover(
            snapshots[1].getLastWrittenEventPosition(),
            snapshots[1].getLastWrittenEventTerm(),
            TRUISM);
    wrapper.wrap(snapshotController.openDb());

    // then
    assertThat(recovered)
        .isEqualTo(StateSnapshotMetadata.createInitial(snapshots[1].getLastWrittenEventTerm()));
    assertThat(wrapper.mayExist(key)).isFalse();
  }
}
