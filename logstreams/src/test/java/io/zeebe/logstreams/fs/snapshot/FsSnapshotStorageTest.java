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

import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.impl.snapshot.fs.FsReadableSnapshot;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorage;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorageConfiguration;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotWriter;
import io.zeebe.logstreams.spi.SnapshotMetadata;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FsSnapshotStorageTest {
  protected static final byte[] SNAPSHOT_DATA = getBytes("snapshot");

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule public ExpectedException thrown = ExpectedException.none();

  private String snapshotRootPath;

  private FsSnapshotStorageConfiguration config;

  private FsSnapshotStorage fsSnapshotStorage;

  @Before
  public void init() throws IOException {
    snapshotRootPath = tempFolder.getRoot().getAbsolutePath();

    config = new FsSnapshotStorageConfiguration();
    config.setRootPath(snapshotRootPath);

    fsSnapshotStorage = new FsSnapshotStorage(config);
  }

  @Test
  public void shouldCreateSnapshot() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);

    final File dataFile = fsSnapshotWriter.getDataFile();
    final File checksumFile = fsSnapshotWriter.getChecksumFile();
    final OutputStream outputStream = fsSnapshotWriter.getOutputStream();

    outputStream.write(SNAPSHOT_DATA);

    fsSnapshotWriter.commit();

    assertThat(dataFile)
        .exists()
        .hasParent(snapshotRootPath)
        .hasName(getFileName(config.snapshotFileName("test", 100)))
        .hasBinaryContent(SNAPSHOT_DATA);

    assertThat(checksumFile)
        .exists()
        .hasParent(snapshotRootPath)
        .hasName(getFileName(config.checksumFileName("test", 100)));
  }

  @Test
  public void shoulNotCreateSnapshotIfSnapshotAlreadyExists() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
    fsSnapshotWriter.commit();

    thrown.expect(RuntimeException.class);
    thrown.expectMessage("Cannot write snapshot");

    fsSnapshotStorage.createSnapshot("test", 100);
  }

  @Test
  public void shouldNotGetLatestSnapshotIfNotExists() throws Exception {
    final FsReadableSnapshot lastSnapshot = fsSnapshotStorage.getLastSnapshot("not-existing");

    assertThat(lastSnapshot).isNull();
  }

  @Test
  public void shouldGetLastSnapshot() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
    fsSnapshotWriter.commit();

    final FsReadableSnapshot lastSnapshot = fsSnapshotStorage.getLastSnapshot("test");

    assertThat(lastSnapshot).isNotNull();
    assertThat(lastSnapshot.getPosition()).isEqualTo(100);

    assertThat(lastSnapshot.getDataFile()).isEqualTo(fsSnapshotWriter.getDataFile());
    assertThat(lastSnapshot.getChecksumFile()).isEqualTo(fsSnapshotWriter.getChecksumFile());
  }

  @Test
  public void shouldGetLastSnapshotWithMultipleFiles() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
    fsSnapshotWriter.commit();

    final FsSnapshotWriter anotherFsSnapshotWriter =
        fsSnapshotStorage.createSnapshot("test-2", 150);
    anotherFsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
    anotherFsSnapshotWriter.commit();

    final FsReadableSnapshot snapshot = fsSnapshotStorage.getLastSnapshot("test");
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getPosition()).isEqualTo(100);

    final FsReadableSnapshot anotherSnapshot = fsSnapshotStorage.getLastSnapshot("test-2");
    assertThat(anotherSnapshot).isNotNull();
    assertThat(anotherSnapshot.getPosition()).isEqualTo(150);
  }

  @Test
  public void shouldNotGetLastSnapshotWithMissingChecksum() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
    fsSnapshotWriter.commit();

    FileUtil.deleteFile(fsSnapshotWriter.getChecksumFile());

    final FsReadableSnapshot snapshot = fsSnapshotStorage.getLastSnapshot("test");
    assertThat(snapshot).isNull();
  }

  @Test
  public void shouldGetLastSnapshotWithUncommittedOnce() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
    fsSnapshotWriter.commit();

    final FsSnapshotWriter newFsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 150);
    newFsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

    final FsReadableSnapshot snapshot = fsSnapshotStorage.getLastSnapshot("test");
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getPosition()).isEqualTo(100);
  }

  @Test
  public void shouldReturnEmptyListIfNoSnapshotsAvailable() {
    // given
    final List<SnapshotMetadata> snapshots = fsSnapshotStorage.listSnapshots();

    // then
    assertThat(snapshots).isEmpty();
  }

  @Test
  public void shouldListLatestVersionOfExistingSnapshots() throws Exception {
    // given
    final List<SnapshotMetadata> snapshots;
    final String firstName = "first";
    final String secondName = "second";

    // when
    writeSnapshot(firstName, 1);
    writeSnapshot(firstName, 3);
    writeSnapshot(secondName, 1);
    snapshots = fsSnapshotStorage.listSnapshots();

    // then
    assertThat(snapshots.size()).isEqualTo(2);

    // when
    final SnapshotMetadata first =
        snapshots.stream().filter((s) -> s.getName().equals("first")).findFirst().get();
    assertThat(first.getPosition()).isEqualTo(3);

    final SnapshotMetadata second =
        snapshots.stream().filter((s) -> s.getName().equals("second")).findFirst().get();
    assertThat(second.getPosition()).isEqualTo(1);
  }

  @Test
  public void shouldIdentifyExistingSnapshotsIfFileIsPresent() throws Exception {
    // given
    final String firstName = "first";

    // when
    writeSnapshot(firstName, 1);
    writeSnapshot(firstName, 3);

    // then
    assertThat(fsSnapshotStorage.snapshotExists(firstName, 3L)).isTrue();
    assertThat(fsSnapshotStorage.snapshotExists(firstName, 1L)).isFalse();
    assertThat(fsSnapshotStorage.snapshotExists("something weird that should not exist", 2L))
        .isFalse();
  }

  protected String getFileName(String absolutePath) {
    return new File(absolutePath).getName();
  }

  private void writeSnapshot(final String name, final long position) throws Exception {
    final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot(name, position);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
    fsSnapshotWriter.commit();
  }
}
