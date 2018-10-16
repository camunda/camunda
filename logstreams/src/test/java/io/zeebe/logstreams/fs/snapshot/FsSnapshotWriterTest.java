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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.zeebe.logstreams.impl.snapshot.fs.FsReadableSnapshot;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotStorageConfiguration;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotWriter;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import org.agrona.BitUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FsSnapshotWriterTest {
  protected static final byte[] SNAPSHOT_DATA = getBytes("snapshot");

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule public ExpectedException thrown = ExpectedException.none();

  private FsSnapshotStorageConfiguration config;

  private File snapshotFile;
  private File checksumFile;

  private FsReadableSnapshot lastSnapshot;

  @Before
  public void init() throws IOException {
    final String snapshotRootPath = tempFolder.getRoot().getAbsolutePath();

    config = new FsSnapshotStorageConfiguration();
    config.setRootPath(snapshotRootPath);

    snapshotFile = tempFolder.newFile("snapshot.snapshot");
    checksumFile = new File(tempFolder.getRoot(), "checksum.sha1");

    lastSnapshot = mock(FsReadableSnapshot.class);
  }

  @Test
  public void shouldWriteSnapshotOnCommit() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter =
        new FsSnapshotWriter(config, snapshotFile, checksumFile, null);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

    fsSnapshotWriter.commit();

    assertThat(snapshotFile).hasBinaryContent(SNAPSHOT_DATA);
  }

  @Test
  public void shouldWriteChecksumOnCommit() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter =
        new FsSnapshotWriter(config, snapshotFile, checksumFile, null);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

    fsSnapshotWriter.commit();

    final byte[] checksum = MessageDigest.getInstance("SHA1").digest(SNAPSHOT_DATA);
    final String hexChecksum = BitUtil.toHex(checksum);

    assertThat(checksumFile)
        .hasContent(config.checksumContent(hexChecksum, snapshotFile.getName()));
  }

  @Test
  public void shouldDeleteSnapshotOnAbort() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter =
        new FsSnapshotWriter(config, snapshotFile, checksumFile, null);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

    fsSnapshotWriter.abort();

    assertThat(snapshotFile).doesNotExist();
    assertThat(checksumFile).doesNotExist();
  }

  @Test
  public void shouldDeleteLastSnapshotOnCommit() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter =
        new FsSnapshotWriter(config, snapshotFile, checksumFile, lastSnapshot);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

    fsSnapshotWriter.commit();

    verify(lastSnapshot).delete();
  }

  @Test
  public void shouldNotDeleteLastSnapshotOnAbort() throws Exception {
    final FsSnapshotWriter fsSnapshotWriter =
        new FsSnapshotWriter(config, snapshotFile, checksumFile, lastSnapshot);
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

    fsSnapshotWriter.abort();

    verify(lastSnapshot, never()).delete();
  }

  @Test
  public void shouldAbortIfCannotValidateChecksum() throws Exception {
    // given
    final FsSnapshotWriter fsSnapshotWriter =
        new FsSnapshotWriter(config, snapshotFile, checksumFile, lastSnapshot);
    final byte[] fakeChecksum = new byte[] {1};

    // when
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

    // then
    assertThatThrownBy(() -> fsSnapshotWriter.validateAndCommit(fakeChecksum))
        .isInstanceOf(RuntimeException.class);

    // assert aborted
    assertThat(snapshotFile).doesNotExist();
    assertThat(checksumFile).doesNotExist();
    verify(lastSnapshot, never()).delete();
  }

  @Test
  public void shouldCommitIfChecksumMatchesExpected() throws Exception {
    // given
    final FsSnapshotWriter fsSnapshotWriter =
        new FsSnapshotWriter(config, snapshotFile, checksumFile, lastSnapshot);
    final MessageDigest digest = MessageDigest.getInstance(config.getChecksumAlgorithm());
    final byte[] checksum = digest.digest(SNAPSHOT_DATA);

    // when
    fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
    fsSnapshotWriter.validateAndCommit(checksum);

    // then
    verify(lastSnapshot).delete();
    assertThat(snapshotFile).exists();
    assertThat(checksumFile).exists();
  }
}
