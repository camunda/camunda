/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import io.zeebe.hashindex.Long2LongHashIndex;
import org.agrona.IoUtil;
import org.junit.After;
import org.junit.Test;

public class HashIndexSnapshotSupportTest
{
    private Long2LongHashIndex hashIndex;
    private HashIndexSnapshotSupport<Long2LongHashIndex> snapshotSupport;

    protected void initIndex(int indexSize, int blockLength)
    {
        hashIndex = new Long2LongHashIndex(indexSize, blockLength);
        snapshotSupport = new HashIndexSnapshotSupport<>(hashIndex);
    }

    @After
    public void closeIndex()
    {
        hashIndex.close();
    }

    @Test
    public void shouldRecover() throws Exception
    {
        initIndex(16, 1);

        hashIndex.put(0, 10);
        hashIndex.put(1, 11);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        snapshotSupport.writeSnapshot(outputStream);

        hashIndex.clear();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        snapshotSupport.recoverFromSnapshot(inputStream);

        assertThat(hashIndex.get(0, -1)).isEqualTo(10);
        assertThat(hashIndex.get(1, -1)).isEqualTo(11);
    }

    @Test
    public void shouldReset() throws Exception
    {
        initIndex(16, 1);

        assertThat(hashIndex.blockCount()).isEqualTo(1);

        hashIndex.put(0, 10);
        hashIndex.put(1, 11);

        assertThat(hashIndex.blockCount()).isEqualTo(2);

        snapshotSupport.reset();

        assertThat(hashIndex.get(0, -1)).isEqualTo(-1);
        assertThat(hashIndex.get(1, -1)).isEqualTo(-1);

        // should only have the initial block
        assertThat(hashIndex.blockCount()).isEqualTo(1);
    }

    @Test
    public void shouldRecoverAnEmptyIndex() throws Exception
    {
        initIndex(16, 1);

        assertThat(hashIndex.blockCount()).isEqualTo(1);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        snapshotSupport.writeSnapshot(outputStream);

        snapshotSupport.reset();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        snapshotSupport.recoverFromSnapshot(inputStream);

        // should only have the initial block
        assertThat(hashIndex.blockCount()).isEqualTo(1);
    }

    @Test
    public void shouldRecoverWhenIndexLargerThanSnapshotBuffer() throws Exception
    {
        // given
        // note: this test uses an internal parameter for setup, which is of course not guaranteed to be
        //   used for anything.
        //   However, this is probably more focused than just testing with a "very large" index
        final int snapshotBufferSize = IoUtil.BLOCK_SIZE;
        final int numEntries = (snapshotBufferSize / 16) + 1;
        initIndex(numEntries, numEntries);

        for (int i = 0; i < numEntries; i++)
        {
            hashIndex.put(i, i);
        }

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // when
        snapshotSupport.writeSnapshot(outputStream);
        hashIndex.clear();

        // then
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        snapshotSupport.recoverFromSnapshot(inputStream);

        for (int i = 0; i < numEntries; i++)
        {
            assertThat(hashIndex.get(i, -1)).isEqualTo(i);
        }
    }
}
