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

import io.zeebe.test.util.AutoCloseableRule;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StateStorageTest {
  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private StateStorage storage;

  @Before
  public void setup() throws Exception {
    final File snapshotsDirectory = tempFolderRule.newFolder("snapshots");
    final File runtimeDirectory = tempFolderRule.newFolder("runtime");
    storage = new StateStorage(runtimeDirectory, snapshotsDirectory);
  }

  @Test
  public void shouldReturnCorrectFolderForMetadata() {
    // given
    final long lastProcessedPosition = 12L;
    final File expected = new File(storage.getSnapshotsDirectory(), "12");

    // when
    final File folder = storage.getSnapshotDirectoryFor(lastProcessedPosition);

    // then
    assertThat(folder).isEqualTo(expected);
  }

  @Test
  public void shouldReturnTempFolder() {
    // given
    final File snapshotsDirectory = storage.getSnapshotsDirectory();
    final File tempSnapshotDirectory = storage.getTempSnapshotDirectory();

    final Path tempParent = tempSnapshotDirectory.toPath().getParent();
    // when
    assertThat(snapshotsDirectory.toPath()).isEqualTo(tempParent);
  }

  @Test
  public void shouldListAllFoldersWhoseNameMatchExpectedPattern() {
    // given
    createSnapshotDirectory("no");
    createSnapshotDirectory("bad");
    createSnapshotDirectory(StateStorage.TMP_SNAPSHOT_DIRECTORY);
    createSnapshotDirectory("1");
    createSnapshotDirectory("0");
    final File[] expected =
        new File[] {
          new File(storage.getSnapshotsDirectory(), "0"),
          new File(storage.getSnapshotsDirectory(), "1")
        };

    // when
    final List<File> valid = storage.list();

    // then
    assertThat(valid).containsExactlyInAnyOrder(expected);
  }

  @Test
  public void shouldFindAllTemporaryFoldersWhichAreBelowGivenPosition() {
    // given
    createSnapshotDirectory("no");
    createSnapshotDirectory("bad");
    createSnapshotDirectory(StateStorage.TMP_SNAPSHOT_DIRECTORY);
    createSnapshotDirectory("121-tmp");
    createSnapshotDirectory("not-tmp");
    createSnapshotDirectory("3-tmp");
    createSnapshotDirectory("3");
    createSnapshotDirectory("310-tmp");
    final File[] expected =
        new File[] {
          new File(storage.getSnapshotsDirectory(), "3-tmp"),
          new File(storage.getSnapshotsDirectory(), "121-tmp")
        };

    // when
    final List<File> valid = storage.findTmpDirectoriesBelowPosition(128L);

    // then
    assertThat(valid).containsExactlyInAnyOrder(expected);
  }

  @Test
  public void shouldListAllFoldersInOrder() {
    // given
    createSnapshotDirectory("no");
    createSnapshotDirectory("45");
    createSnapshotDirectory("bad");
    createSnapshotDirectory("256");
    createSnapshotDirectory("131");
    createSnapshotDirectory(StateStorage.TMP_SNAPSHOT_DIRECTORY);
    createSnapshotDirectory("1");
    createSnapshotDirectory("0");

    final File[] expected =
        new File[] {
          new File(storage.getSnapshotsDirectory(), "0"),
          new File(storage.getSnapshotsDirectory(), "1"),
          new File(storage.getSnapshotsDirectory(), "45"),
          new File(storage.getSnapshotsDirectory(), "131"),
          new File(storage.getSnapshotsDirectory(), "256")
        };

    // when/then
    assertThat(storage.listByPositionAsc()).containsExactly(expected);

    final List<File> reverseOrder = Arrays.asList(expected);
    Collections.reverse(reverseOrder);
    assertThat(storage.listByPositionDesc()).containsExactlyElementsOf(reverseOrder);
  }

  private File createSnapshotDirectory(final String name) {
    final File directory = new File(storage.getSnapshotsDirectory(), name);
    directory.mkdir();

    return directory;
  }
}
