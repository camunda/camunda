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

import io.zeebe.map.Bytes2LongZbMap;
import io.zeebe.map.Long2BytesZbMap;
import io.zeebe.map.Long2LongZbMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static io.zeebe.map.ZbMap.OPTIMAL_BLOCK_COUNT;
import static io.zeebe.map.ZbMap.OPTIMAL_TABLE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

public class ComposedZbMapSnapshotTest
{
    public static final int IDX_ENTRY_COUNT = 1_000_000;
    private Long2LongZbMap long2LongMap;
    private Bytes2LongZbMap bytes2LongMap;
    private Long2BytesZbMap long2BytesMap;

    private ZbMapSnapshotSupport<Long2LongZbMap> long2LongSnapshotSupport;
    private ZbMapSnapshotSupport<Bytes2LongZbMap> bytes2LongSnapshotSupport;
    private ZbMapSnapshotSupport<Long2BytesZbMap> long2bytesSnapshotSupport;


    private File snapshotFile;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() throws IOException
    {
        long2LongMap = new Long2LongZbMap(OPTIMAL_TABLE_SIZE, OPTIMAL_BLOCK_COUNT);
        long2LongMap.put(15, 15);

        long2BytesMap = new Long2BytesZbMap(OPTIMAL_TABLE_SIZE, OPTIMAL_BLOCK_COUNT, 16);
        long2BytesMap.put(16, "16".getBytes());

        bytes2LongMap = new Bytes2LongZbMap(OPTIMAL_TABLE_SIZE, OPTIMAL_BLOCK_COUNT, 16);
        bytes2LongMap.put("17".getBytes(), 17);

        long2bytesSnapshotSupport = new ZbMapSnapshotSupport<>(long2BytesMap);
        long2LongSnapshotSupport = new ZbMapSnapshotSupport<>(long2LongMap);
        bytes2LongSnapshotSupport = new ZbMapSnapshotSupport<>(bytes2LongMap);

        snapshotFile = tempFolder.newFile("snapshot");
    }

    @After
    public void cleanUp()
    {
        long2BytesMap.close();
        long2LongMap.close();
        bytes2LongMap.close();
    }

    @Test
    public void shouldWriteAndRecoverLargeSnapshot() throws Exception
    {
        // given
        final Long2LongZbMap largeIndex = new Long2LongZbMap(IDX_ENTRY_COUNT / OPTIMAL_BLOCK_COUNT, OPTIMAL_BLOCK_COUNT);
        final ZbMapSnapshotSupport<Long2LongZbMap> long2LongSnapshotSupport = new ZbMapSnapshotSupport<>(largeIndex);
        final ComposedZbMapSnapshot composedSnapshot = new ComposedZbMapSnapshot(long2LongSnapshotSupport);

        for (long idx = 0; idx < IDX_ENTRY_COUNT; idx++)
        {
            largeIndex.put(idx, idx);
        }

        // when
        composedSnapshot.writeSnapshot(new FileOutputStream(snapshotFile));
        final long processedBytes = composedSnapshot.getProcessedBytes();

        // then no error during write occurs
        largeIndex.clear();

        // and when
        composedSnapshot.recoverFromSnapshot(new FileInputStream(snapshotFile));

        // then all values are recovered
        assertThat(composedSnapshot.getProcessedBytes()).isEqualTo(processedBytes);
        for (long idx = 0; idx < IDX_ENTRY_COUNT; idx++)
        {
            assertThat(largeIndex.get(idx, -1)).isEqualTo(idx);
        }
        largeIndex.close();
    }

    @Test
    public void shouldRecoverParts() throws Exception
    {
        // given
        final ComposedZbMapSnapshot composedSnapshot =
            new ComposedZbMapSnapshot(long2bytesSnapshotSupport,
                                          long2LongSnapshotSupport,
                                          bytes2LongSnapshotSupport);
        composedSnapshot.writeSnapshot(new FileOutputStream(snapshotFile));
        final long processedBytes = composedSnapshot.getProcessedBytes();

        long2LongMap.clear();
        long2BytesMap.clear();
        bytes2LongMap.clear();

        // when
        composedSnapshot.recoverFromSnapshot(new FileInputStream(snapshotFile));

        // then
        assertThat(composedSnapshot.getProcessedBytes()).isEqualTo(processedBytes);

        assertThat(long2LongMap.get(15, -1)).isEqualTo(15);

        final byte bytes[] = new byte[2];
        assertThat(long2BytesMap.get(16, bytes)).isTrue();
        assertThat(bytes).isEqualTo("16".getBytes());

        assertThat(bytes2LongMap.get("17".getBytes(), -1)).isEqualTo(17);
    }

    @Test
    public void shouldFailIfSnapshotHaveNoParts() throws Exception
    {
        thrown.expect(IllegalArgumentException.class);

        new ComposedZbMapSnapshot();
    }

    @Test
    public void shouldFailToRecoverIfPartsAreLessThanSnapshot() throws Exception
    {
        new ComposedZbMapSnapshot(long2bytesSnapshotSupport,
            long2LongSnapshotSupport,
            bytes2LongSnapshotSupport).writeSnapshot(new FileOutputStream(snapshotFile));

        thrown.expect(IllegalStateException.class);

        new ComposedZbMapSnapshot(long2bytesSnapshotSupport).recoverFromSnapshot(new FileInputStream(snapshotFile));
    }

    @Test
    public void shouldFailToRecoverIfPartsAreMoreThanSnapshot() throws Exception
    {
        new ComposedZbMapSnapshot(long2bytesSnapshotSupport,
            long2LongSnapshotSupport).writeSnapshot(new FileOutputStream(snapshotFile));

        thrown.expect(IllegalStateException.class);

        new ComposedZbMapSnapshot(long2bytesSnapshotSupport,
            long2LongSnapshotSupport, bytes2LongSnapshotSupport).recoverFromSnapshot(new FileInputStream(snapshotFile));
    }

}
