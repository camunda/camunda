/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.logstreams.fs.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

import org.agrona.BitUtil;
import org.camunda.tngp.logstreams.impl.fs.FsReadableSnapshot;
import org.camunda.tngp.logstreams.impl.fs.FsSnapshotStorageConfiguration;
import org.camunda.tngp.logstreams.impl.fs.FsSnapshotWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FsSnapshotWriterTest
{
    protected static final byte[] SNAPSHOT_DATA = "snapshot".getBytes();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private FsSnapshotStorageConfiguration config;

    private File snapshotFile;
    private File checksumFile;

    private FsReadableSnapshot lastSnapshot;

    @Before
    public void init() throws IOException
    {
        final String snapshotRootPath = tempFolder.getRoot().getAbsolutePath();

        config = new FsSnapshotStorageConfiguration();
        config.setRootPath(snapshotRootPath);

        snapshotFile = tempFolder.newFile("snapshot.data");
        checksumFile = tempFolder.newFile("checksum.sha1");

        final File lastSnapshotFile = tempFolder.newFile("last-snapshot.data");
        final File lastChecksumFile = tempFolder.newFile("last-snapshot.sha1");

        lastSnapshot = mock(FsReadableSnapshot.class);
        when(lastSnapshot.getDataFile()).thenReturn(lastSnapshotFile);
        when(lastSnapshot.getChecksumFile()).thenReturn(lastChecksumFile);
    }

    @Test
    public void shouldWriteSnapshotOnCommit() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = new FsSnapshotWriter(config, snapshotFile, checksumFile, null);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

        fsSnapshotWriter.commit();

        assertThat(snapshotFile).hasBinaryContent(SNAPSHOT_DATA);
    }

    @Test
    public void shouldWriteChecksumOnCommit() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = new FsSnapshotWriter(config, snapshotFile, checksumFile, null);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

        fsSnapshotWriter.commit();

        final byte[] checksum = MessageDigest.getInstance("SHA1").digest(SNAPSHOT_DATA);
        final String hexChecksum = BitUtil.toHex(checksum);

        assertThat(checksumFile).hasContent(config.checksumContent(hexChecksum, snapshotFile.getName()));
    }

    @Test
    public void shouldDeleteSnapshotOnAbort() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = new FsSnapshotWriter(config, snapshotFile, checksumFile, null);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

        fsSnapshotWriter.abort();

        assertThat(snapshotFile).doesNotExist();
        assertThat(checksumFile).doesNotExist();
    }

    @Test
    public void shouldDeleteLastSnapshotOnCommit() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = new FsSnapshotWriter(config, snapshotFile, checksumFile, lastSnapshot);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

        fsSnapshotWriter.commit();

        assertThat(lastSnapshot.getDataFile()).doesNotExist();
        assertThat(lastSnapshot.getChecksumFile()).doesNotExist();
    }

    @Test
    public void shouldNotDeleteLastSnapshotOnAbort() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = new FsSnapshotWriter(config, snapshotFile, checksumFile, lastSnapshot);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

        fsSnapshotWriter.abort();

        assertThat(lastSnapshot.getDataFile()).exists();
        assertThat(lastSnapshot.getChecksumFile()).exists();
    }

}
