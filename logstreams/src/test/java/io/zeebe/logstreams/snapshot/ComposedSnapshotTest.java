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
package io.zeebe.logstreams.snapshot;

import io.zeebe.hashindex.Bytes2LongHashIndex;
import io.zeebe.hashindex.Long2BytesHashIndex;
import io.zeebe.hashindex.Long2LongHashIndex;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static io.zeebe.hashindex.HashIndex.OPTIMAL_BUCKET_COUNT;
import static io.zeebe.hashindex.HashIndex.OPTIMAL_INDEX_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

public class ComposedSnapshotTest
{
    public static final int IDX_ENTRY_COUNT = 10_000_000;
    private Long2LongHashIndex long2LongHashIndex;
    private Bytes2LongHashIndex bytes2LongHashIndex;
    private Long2BytesHashIndex long2BytesHashIndex;

    private HashIndexSnapshotSupport<Long2LongHashIndex> long2LongSnapshotSupport;
    private HashIndexSnapshotSupport<Bytes2LongHashIndex> bytes2LongSnapshotSupport;
    private HashIndexSnapshotSupport<Long2BytesHashIndex> long2bytesSnapshotSupport;


    private File snapshotFile;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() throws IOException
    {
        long2LongHashIndex = new Long2LongHashIndex(OPTIMAL_INDEX_SIZE, OPTIMAL_BUCKET_COUNT);
        long2LongHashIndex.put(15, 15);

        long2BytesHashIndex = new Long2BytesHashIndex(OPTIMAL_INDEX_SIZE, OPTIMAL_BUCKET_COUNT, 16);
        long2BytesHashIndex.put(16, "16".getBytes());

        bytes2LongHashIndex = new Bytes2LongHashIndex(OPTIMAL_INDEX_SIZE, OPTIMAL_BUCKET_COUNT, 16);
        bytes2LongHashIndex.put("17".getBytes(), 17);

        long2bytesSnapshotSupport = new HashIndexSnapshotSupport<>(long2BytesHashIndex);
        long2LongSnapshotSupport = new HashIndexSnapshotSupport<>(long2LongHashIndex);
        bytes2LongSnapshotSupport = new HashIndexSnapshotSupport<>(bytes2LongHashIndex);

        snapshotFile = tempFolder.newFile("snapshot");
    }

    @Test

    public void shouldWriteAndRecoverLargeSnapshot() throws Exception
    {
        // given
        final Long2LongHashIndex largeIndex = new Long2LongHashIndex(IDX_ENTRY_COUNT / OPTIMAL_BUCKET_COUNT, OPTIMAL_BUCKET_COUNT);
        final HashIndexSnapshotSupport<Long2LongHashIndex> long2LongSnapshotSupport = new HashIndexSnapshotSupport<>(largeIndex);
        final ComposedHashIndexSnapshot composedSnapshot = new ComposedHashIndexSnapshot(long2LongSnapshotSupport);

        for (long idx = 0; idx < IDX_ENTRY_COUNT; idx++)
        {
            largeIndex.put(idx, idx);
        }

        // when
        composedSnapshot.writeSnapshot(new FileOutputStream(snapshotFile));

        // then no error occurs
        largeIndex.clear();

        // and when
        composedSnapshot.recoverFromSnapshot(new FileInputStream(snapshotFile));


        for (long idx = 0; idx < IDX_ENTRY_COUNT; idx++)
        {
            assertThat(largeIndex.get(idx, -1)).isEqualTo(idx);
        }
    }

    @Test
    public void shouldRecoverParts() throws Exception
    {
        // given
        final ComposedHashIndexSnapshot composedSnapshot =
            new ComposedHashIndexSnapshot(long2bytesSnapshotSupport,
                                          long2LongSnapshotSupport,
                                          bytes2LongSnapshotSupport);
        composedSnapshot.writeSnapshot(new FileOutputStream(snapshotFile));

        long2LongHashIndex.clear();
        long2BytesHashIndex.clear();
        bytes2LongHashIndex.clear();

        // when
        composedSnapshot.recoverFromSnapshot(new FileInputStream(snapshotFile));

        // then
        assertThat(long2LongHashIndex.get(15, -1)).isEqualTo(15);

        final byte bytes[] = new byte[2];
        assertThat(long2BytesHashIndex.get(16, bytes)).isTrue();
        assertThat(bytes).isEqualTo("16".getBytes());

        assertThat(bytes2LongHashIndex.get("17".getBytes(), -1)).isEqualTo(17);
    }

    @Test
    public void shouldFailIfSnapshotHaveNoParts() throws Exception
    {
        thrown.expect(IllegalArgumentException.class);

        new ComposedHashIndexSnapshot();
    }

    @Test
    public void shouldFailToRecoverIfPartsAreLessThanSnapshot() throws Exception
    {
        new ComposedHashIndexSnapshot(long2bytesSnapshotSupport,
            long2LongSnapshotSupport,
            bytes2LongSnapshotSupport).writeSnapshot(new FileOutputStream(snapshotFile));

        thrown.expect(IllegalStateException.class);

        new ComposedHashIndexSnapshot(long2bytesSnapshotSupport).recoverFromSnapshot(new FileInputStream(snapshotFile));
    }

    @Test
    public void shouldFailToRecoverIfPartsAreMoreThanSnapshot() throws Exception
    {
        new ComposedHashIndexSnapshot(long2bytesSnapshotSupport,
            long2LongSnapshotSupport).writeSnapshot(new FileOutputStream(snapshotFile));

        thrown.expect(IllegalStateException.class);

        new ComposedHashIndexSnapshot(long2bytesSnapshotSupport,
            long2LongSnapshotSupport, bytes2LongSnapshotSupport).recoverFromSnapshot(new FileInputStream(snapshotFile));
    }

}
