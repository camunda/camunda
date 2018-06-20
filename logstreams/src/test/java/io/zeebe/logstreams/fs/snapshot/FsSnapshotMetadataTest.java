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

import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotMetadata;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorageConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.agrona.BitUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FsSnapshotMetadataTest {
  private static final byte[] SNAPSHOT_DATA = getBytes("snapshot");

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule public ExpectedException thrown = ExpectedException.none();

  private FsSnapshotStorageConfiguration config;

  private File snapshotFile;
  private File checksumFile;

  @Before
  public void init() throws Exception {
    final String snapshotRootPath = tempFolder.getRoot().getAbsolutePath();

    config = new FsSnapshotStorageConfiguration();
    config.setRootPath(snapshotRootPath);

    snapshotFile = createDataFile("snapshot-00.snapshot", SNAPSHOT_DATA);
    checksumFile = createChecksumFile("snapshot-00.sha1", SNAPSHOT_DATA, snapshotFile.getName());
  }

  @Test
  public void shouldGetPosition() {
    final FsSnapshotMetadata snapshotMetadata =
        new FsSnapshotMetadata(config, snapshotFile, checksumFile, 100);

    assertThat(snapshotMetadata.getPosition()).isEqualTo(100);
  }

  @Test
  public void shouldFailToValidateIfFileNameDoesntMatch() throws Exception {
    final File corruptedChecksumFile =
        createChecksumFile("corrupted-checksum.sha1", SNAPSHOT_DATA, "other-snapshot.snapshot");

    thrown.expect(RuntimeException.class);
    thrown.expectMessage("Read invalid snapshot, file name doesn't match");

    new FsSnapshotMetadata(config, snapshotFile, corruptedChecksumFile, 100);
  }

  @Test
  public void shouldFailToValidateIfChecksumFileIsEmpty() throws Exception {
    final File emptyChecksumFile = tempFolder.newFile("invalid-checksum.sha1");

    thrown.expect(RuntimeException.class);
    thrown.expectMessage("Read invalid checksum file, no content");

    new FsSnapshotMetadata(config, snapshotFile, emptyChecksumFile, 100);
  }

  @Test
  public void shouldFailToValidateIfChecksumFileIsInvalid() throws Exception {
    final File invalidChecksumFile = tempFolder.newFile("invalid-checksum.sha1");
    Files.write(invalidChecksumFile.toPath(), getBytes("invalid-content"));

    thrown.expect(RuntimeException.class);
    thrown.expectMessage("Read invalid checksum file");

    new FsSnapshotMetadata(config, snapshotFile, invalidChecksumFile, 100);
  }

  @Test
  public void shouldReturnIdAsSnapshotName() {
    // given
    final FsSnapshotMetadata snapshot =
        new FsSnapshotMetadata(config, snapshotFile, checksumFile, 100);

    // then
    assertThat(snapshot.getName()).isEqualTo("snapshot");
  }

  @Test
  public void shouldReturnCorrectLengthForGivenSnapshot() {
    // given
    final FsSnapshotMetadata snapshot =
        new FsSnapshotMetadata(config, snapshotFile, checksumFile, 100);

    // then
    assertThat(snapshot.getSize()).isEqualTo(SNAPSHOT_DATA.length);
  }

  @Test
  public void shouldReturnCorrectChecksum() throws Exception {
    // given
    final FsSnapshotMetadata snapshot =
        new FsSnapshotMetadata(config, snapshotFile, checksumFile, 100);
    final byte[] checksum = MessageDigest.getInstance("SHA1").digest(SNAPSHOT_DATA);

    // then
    assertThat(snapshot.getChecksum()).isEqualTo(checksum);
  }

  private File createDataFile(final String fileName, final byte[] content) throws IOException {
    final File dataFile = tempFolder.newFile(fileName);
    Files.write(dataFile.toPath(), content);

    return dataFile;
  }

  private File createChecksumFile(final String fileName, final byte[] data, String dataFileName)
      throws Exception {
    final byte[] checksum = MessageDigest.getInstance("SHA1").digest(data);
    final String hexChecksum = BitUtil.toHex(checksum);
    final String checksumFileContent = config.checksumContent(hexChecksum, dataFileName);

    final File checksumFile = tempFolder.newFile(fileName);
    Files.write(checksumFile.toPath(), getBytes(checksumFileContent));

    return checksumFile;
  }
}
