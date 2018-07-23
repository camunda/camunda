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

import io.zeebe.test.util.AutoCloseableRule;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StateStorageTest {
  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private File root;
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
    final int lastWrittenEventTerm = 3;
    final long lastSuccessfulProcessedEventPosition = 13L;
    final long lastWrittenEventPosition = 12L;
    final File expected = new File(storage.getSnapshotsDirectory(), "13_12_3");

    // when
    final StateSnapshotMetadata metadata =
        new StateSnapshotMetadata(
            lastSuccessfulProcessedEventPosition,
            lastWrittenEventPosition,
            lastWrittenEventTerm,
            false);
    final File folder = storage.getSnapshotDirectoryFor(metadata);

    // then
    assertThat(folder).isEqualTo(expected);
  }

  @Test
  public void shouldReturnMetadataForExistingFolder() throws IOException {
    // given
    final File folder = tempFolderRule.newFolder("13_12_3");

    // when
    final StateSnapshotMetadata metadata = storage.getSnapshotMetadata(folder);

    // then
    assertThat(metadata.getLastSuccessfulProcessedEventPosition()).isEqualTo(13L);
    assertThat(metadata.getLastWrittenEventPosition()).isEqualTo(12L);
    assertThat(metadata.getLastWrittenEventTerm()).isEqualTo(3);
    assertThat(metadata.exists()).isTrue();
  }

  @Test
  public void shouldReturnMetadataForNonExistingFolder() {
    // given
    final File folder = new File(tempFolderRule.getRoot(), "1_2_0");

    // when
    final StateSnapshotMetadata metadata = storage.getSnapshotMetadata(folder);

    // then
    assertThat(metadata.getLastSuccessfulProcessedEventPosition()).isEqualTo(1L);
    assertThat(metadata.getLastWrittenEventPosition()).isEqualTo(2L);
    assertThat(metadata.getLastWrittenEventTerm()).isEqualTo(0);
    assertThat(metadata.exists()).isFalse();
  }

  @Test
  public void shouldThrowIllegalArgumentExceptionIfFolderIsNotADirectory() throws IOException {
    // given
    final File file = tempFolderRule.newFile("13_12_3");

    // then
    assertThatThrownBy(() -> storage.getSnapshotMetadata(file))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldListAllFoldersWhoseNameMatchExpectedPattern() throws IOException {
    // given
    final File[] folders =
        new File[] {
          createSnapshotDirectory("no"),
          createSnapshotDirectory("bad"),
          createSnapshotDirectory("1_2_0"),
          createSnapshotDirectory("0_0_0"),
          createSnapshotDirectory("1"),
          createSnapshotDirectory("1_2")
        };
    final StateSnapshotMetadata[] expected =
        new StateSnapshotMetadata[] {
          new StateSnapshotMetadata(1, 2, 0, true), new StateSnapshotMetadata(0, 0, 0, true)
        };

    // when
    final List<StateSnapshotMetadata> valid = storage.list();

    // then
    assertThat(valid).containsExactlyInAnyOrder(expected);
  }

  @Test
  public void shouldListAllRecoverableSnapshotsBasedOnGivenPosition() throws IOException {
    // given
    final File[] folders =
        new File[] {
          createSnapshotDirectory("1_2_0"),
          createSnapshotDirectory("2_3_0"),
          createSnapshotDirectory("3_4_0")
        };
    final StateSnapshotMetadata[] expected =
        new StateSnapshotMetadata[] {
          new StateSnapshotMetadata(1, 2, 0, true), new StateSnapshotMetadata(2, 3, 0, true)
        };

    // when
    final List<StateSnapshotMetadata> valid = storage.listRecoverable(3L);

    // then
    assertThat(valid).containsExactlyInAnyOrder(expected);
  }

  private File createSnapshotDirectory(final String name) {
    final File directory = new File(storage.getSnapshotsDirectory(), name);
    directory.mkdir();

    return directory;
  }
}
