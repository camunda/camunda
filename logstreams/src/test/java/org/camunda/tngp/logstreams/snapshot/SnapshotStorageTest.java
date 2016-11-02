package org.camunda.tngp.logstreams.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class SnapshotStorageTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldReturnNullForNonExistingSnapshot() throws Exception
    {
        // given
        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(tempFolder.getRoot().getAbsolutePath()).build();

        // then
        assertThat(snapshotStorage.getLastSnapshot("foo")).isNull();
    }

    @Test
    public void shouldCreateSnapshot() throws Exception
    {
        // given
        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(tempFolder.getRoot().getAbsolutePath()).build();

        // when
        final SnapshotWriter writer = snapshotStorage.createSnapshot("foo", 100L);
        writeSnapshot(writer, "some bytes");

        // then
        final ReadableSnapshot lastSnapshot = snapshotStorage.getLastSnapshot("foo");
        assertThat(lastSnapshot).isNotNull();
        assertThat(lastSnapshot.getPosition()).isEqualTo(100L);
        readAndValidate(lastSnapshot, "some bytes");
    }

    protected void readAndValidate(ReadableSnapshot snapshot, String expectedData) throws Exception
    {
        final InputStream data = snapshot.getData();
        assertThat(data).hasSameContentAs(new ByteArrayInputStream(expectedData.getBytes(StandardCharsets.UTF_8)));
        snapshot.validateAndClose();
    }

    protected void writeSnapshot(SnapshotWriter writer, String dataToWrite) throws Exception
    {
        final OutputStream outputStream = writer.getOutputStream();
        outputStream.write(dataToWrite.getBytes(StandardCharsets.UTF_8));
        writer.commit();
    }

}
