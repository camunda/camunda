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
package io.zeebe.logstreams.fs.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotController;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorage;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorageConfiguration;
import io.zeebe.logstreams.processor.StringValueSnapshot;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.logstreams.state.StateSnapshotMetadata;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FsSnapshotControllerTest {
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private FsSnapshotStorage storage;

  @Before
  public void setup() {
    final FsSnapshotStorageConfiguration storageConfiguration =
        new FsSnapshotStorageConfiguration();
    storageConfiguration.setRootPath(tempFolder.getRoot().getAbsolutePath());

    storage = new FsSnapshotStorage(storageConfiguration);
  }

  @Test
  public void shouldNotTakeSnapshotUnlessNewRecordCommitted() throws Exception {
    // given
    final long commitPosition = -1L;
    final SnapshotSupport resource = new StringValueSnapshot();
    final FsSnapshotController controller = new FsSnapshotController(storage, "res", resource);
    final StateSnapshotMetadata metadata =
        new StateSnapshotMetadata(commitPosition, commitPosition, 1, false);

    // when
    controller.takeSnapshot(metadata, commitPosition);

    // then
    assertThat(storage.listSnapshots()).hasSize(0);
  }

  @Test
  public void shouldNotTakeSnapshotIfUncommittedRecords() throws Exception {
    // given
    final long commitPosition = 3L;
    final SnapshotSupport resource = new StringValueSnapshot();
    final FsSnapshotController controller = new FsSnapshotController(storage, "res", resource);
    final StateSnapshotMetadata metadata =
        new StateSnapshotMetadata(commitPosition, commitPosition + 1, 1, false);

    // when
    controller.takeSnapshot(metadata, commitPosition);

    // then
    assertThat(storage.listSnapshots()).hasSize(0);
  }

  @Test
  public void shouldNotWriteSnapshotWithNegativePosition() throws Exception {
    // given
    final long commitPosition = 1L;
    final SnapshotSupport resource = new StringValueSnapshot();
    final FsSnapshotController controller = new FsSnapshotController(storage, "res", resource);
    final StateSnapshotMetadata metadata = new StateSnapshotMetadata(-1, commitPosition, 1, false);

    // when
    controller.takeSnapshot(metadata, commitPosition);

    // then
    assertThat(storage.listSnapshots()).hasSize(0);
  }
}
