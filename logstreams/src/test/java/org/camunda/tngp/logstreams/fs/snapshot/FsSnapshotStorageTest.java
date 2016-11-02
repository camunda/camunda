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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.camunda.tngp.logstreams.impl.fs.FsReadableSnapshot;
import org.camunda.tngp.logstreams.impl.fs.FsSnapshotStorage;
import org.camunda.tngp.logstreams.impl.fs.FsSnapshotStorageConfiguration;
import org.camunda.tngp.logstreams.impl.fs.FsSnapshotWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FsSnapshotStorageTest
{
    protected static final byte[] SNAPSHOT_DATA = "snapshot".getBytes();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private String snapshotRootPath;

    private FsSnapshotStorageConfiguration config;

    private FsSnapshotStorage fsSnapshotStorage;

    @Before
    public void init() throws IOException
    {
        snapshotRootPath = tempFolder.getRoot().getAbsolutePath();

        config = new FsSnapshotStorageConfiguration();
        config.setRootPath(snapshotRootPath);

        fsSnapshotStorage = new FsSnapshotStorage(config);
    }

    @Test
    public void shouldCreateSnapshot() throws Exception
    {
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
    public void shoulNotCreateSnapshotIfSnapshotAlreadyExists() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
        fsSnapshotWriter.commit();

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot write snapshot");

        fsSnapshotStorage.createSnapshot("test", 100);
    }

    @Test
    public void shoulNotCreateSnapshotIfChecksumAlreadyExists() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
        fsSnapshotWriter.commit();

        fsSnapshotWriter.getDataFile().delete();

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot write snapshot checksum");

        fsSnapshotStorage.createSnapshot("test", 100);
    }

    @Test
    public void shouldNotGetLatestSnapshotIfNotExists() throws Exception
    {
        final FsReadableSnapshot lastSnapshot = fsSnapshotStorage.getLastSnapshot("not-existing");

        assertThat(lastSnapshot).isNull();
    }

    @Test
    public void shouldGetLastSnapshot() throws Exception
    {
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
    public void shouldGetLastSnapshotWithMultipleFiles() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
        fsSnapshotWriter.commit();

        final FsSnapshotWriter anotherFsSnapshotWriter = fsSnapshotStorage.createSnapshot("test-2", 150);
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
    public void shouldNotGetLastSnapshotWithMissingChecksum() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
        fsSnapshotWriter.commit();

        fsSnapshotWriter.getChecksumFile().delete();

        final FsReadableSnapshot snapshot = fsSnapshotStorage.getLastSnapshot("test");
        assertThat(snapshot).isNull();

        // should delete snapshot if no checksum exists
        assertThat(fsSnapshotWriter.getDataFile()).doesNotExist();
    }

    @Test
    public void shouldGetLastSnapshotWithUncommittedOnce() throws Exception
    {
        final FsSnapshotWriter fsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 100);
        fsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);
        fsSnapshotWriter.commit();

        final FsSnapshotWriter newFsSnapshotWriter = fsSnapshotStorage.createSnapshot("test", 150);
        newFsSnapshotWriter.getOutputStream().write(SNAPSHOT_DATA);

        final FsReadableSnapshot snapshot = fsSnapshotStorage.getLastSnapshot("test");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getPosition()).isEqualTo(100);
    }

    protected String getFileName(String absolutePath)
    {
        return new File(absolutePath).getName();
    }

}
