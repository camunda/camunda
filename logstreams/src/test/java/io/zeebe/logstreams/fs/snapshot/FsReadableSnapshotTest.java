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
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorageConfiguration;
import io.zeebe.logstreams.snapshot.InvalidSnapshotException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.agrona.BitUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FsReadableSnapshotTest {
  protected static final byte[] SNAPSHOT_DATA = getBytes("snapshot");

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule public ExpectedException thrown = ExpectedException.none();

  private FsSnapshotStorageConfiguration config;

  private File dataFile;
  private File checksumFile;

  @Before
  public void init() throws Exception {
    final String snapshotRootPath = tempFolder.getRoot().getAbsolutePath();

    config = new FsSnapshotStorageConfiguration();
    config.setRootPath(snapshotRootPath);

    dataFile = createDataFile("snapshot-00.snapshot", SNAPSHOT_DATA);
    checksumFile = createChecksumFile("snapshot-00.sha1", SNAPSHOT_DATA, dataFile.getName());
  }

  @Test
  public void shouldGetData() {
    final FsReadableSnapshot fsReadableSnapshot =
        new FsReadableSnapshot(config, dataFile, checksumFile, 100);

    final InputStream dataInputStream = fsReadableSnapshot.getData();

    assertThat(dataInputStream).isNotNull();
    assertThat(dataInputStream).hasSameContentAs(new ByteArrayInputStream(SNAPSHOT_DATA));
  }

  @Test
  public void shouldValidateData() throws Exception {
    final FsReadableSnapshot fsReadableSnapshot =
        new FsReadableSnapshot(config, dataFile, checksumFile, 100);

    readCompleteStream(fsReadableSnapshot.getData());

    // the data is valid if no exception occurs
    fsReadableSnapshot.validateAndClose();
  }

  @Test
  public void shouldDeleteFiles() throws Exception {
    final FsReadableSnapshot fsReadableSnapshot =
        new FsReadableSnapshot(config, dataFile, checksumFile, 100);

    fsReadableSnapshot.delete();

    assertThat(dataFile).doesNotExist();
    assertThat(checksumFile).doesNotExist();
  }

  @Test
  public void shouldFailToValidateIfChecksumDoesntMatch() throws Exception {
    final File corruptedChecksumFile =
        createChecksumFile(
            "corrupted-checksum.sha1", getBytes("corrupted-data"), dataFile.getName());

    final FsReadableSnapshot fsReadableSnapshot =
        new FsReadableSnapshot(config, dataFile, corruptedChecksumFile, 100);

    readCompleteStream(fsReadableSnapshot.getData());

    thrown.expect(InvalidSnapshotException.class);
    thrown.expectMessage("Read invalid snapshot, checksum doesn't match");

    fsReadableSnapshot.validateAndClose();
  }

  protected File createDataFile(String fileName, byte[] content) throws IOException {
    final File dataFile = tempFolder.newFile(fileName);
    Files.write(dataFile.toPath(), content);

    return dataFile;
  }

  protected File createChecksumFile(String fileName, byte[] data, String dataFileName)
      throws Exception {
    final byte[] checksum = MessageDigest.getInstance("SHA1").digest(data);
    final String hexChecksum = BitUtil.toHex(checksum);
    final String checksumFileContent = config.checksumContent(hexChecksum, dataFileName);

    final File checksumFile = tempFolder.newFile(fileName);
    Files.write(checksumFile.toPath(), getBytes(checksumFileContent));

    return checksumFile;
  }

  protected void readCompleteStream(InputStream data) throws IOException {
    while (data.read() >= 0) {
      // read stream
    }
  }
}
