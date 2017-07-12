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
package io.zeebe.hashindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.zeebe.test.util.io.RepeatedlyFailingInputStream;
import io.zeebe.test.util.io.ShortReadInputStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IndexSerializerTest
{
    private static final int DATASET_SIZE = 100_000;
    private static final int NO_SUCH_KEY = -1;

    private Long2LongHashIndex index;
    private IndexSerializer indexSerializer;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();


    @Before
    public void createIndex() throws Exception
    {
        index = new Long2LongHashIndex(DATASET_SIZE / 16, 16);
        indexSerializer = new IndexSerializer();
    }

    @After
    public void close()
    {
        index.close();
    }

    @Test
    public void shouldRestoreEmptyIndex() throws IOException
    {
        writeAndReadIndex(index);

        for (int i = 0; i < DATASET_SIZE; i++)
        {
            assertThat(index.get(i, NO_SUCH_KEY)).isEqualTo(NO_SUCH_KEY);
        }
    }

    @Test
    public void shouldRestoreFullIndex() throws IOException
    {
        fillIndex(index);

        writeAndReadIndex(index);

        for (int i = 0; i < DATASET_SIZE; i++)
        {
            assertThat(index.get(i, NO_SUCH_KEY)).isEqualTo(i);
        }
    }

    @Test
    public void shouldRestoreFullIndexWithTemporaryReadErrors() throws IOException
    {
        // given
        fillIndex(index);
        final InputStream inputStream = writeIndex(index);
        index.clear();

        // when
        readIndex(index, new RepeatedlyFailingInputStream(inputStream));

        // then
        for (int i = 0; i < DATASET_SIZE; i++)
        {
            assertThat(index.get(i, NO_SUCH_KEY)).isEqualTo(i);
        }
    }

    @Test
    public void shouldThrowOnShortReadOfIndexBuffer() throws IOException
    {
        // given
        fillIndex(index);
        final InputStream inputStream = new ShortReadInputStream(writeIndex(index), 1024, false);

        expectedException.expect(IOException.class);
        expectedException.expectMessage("Unable to read full index buffer from input stream. " +
            "Only read 1020 bytes but expected 65536 bytes.");

        // when
        readIndex(index, inputStream);
    }

    @Test
    public void shouldThrowOnFailedReadOfIndexBuffer() throws IOException
    {
        // given
        fillIndex(index);
        final InputStream inputStream = new ShortReadInputStream(writeIndex(index), 1025, true);

        expectedException.expect(IOException.class);
        expectedException.expectMessage("Read failure");

        // when
        readIndex(index, inputStream);
    }

    @Test
    public void shouldThrowOnIllegalVersion() throws IOException
    {
        final IndexSerializer indexSerializer = new IndexSerializer();
        indexSerializer.wrap(index);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        indexSerializer.writeToStream(out);

        index.clear();

        final byte[] byteArray = out.toByteArray();

        // set illegal version
        new UnsafeBuffer(byteArray).putInt(0, 1001);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);

        expectedException.expect(RuntimeException.class);
        indexSerializer.readFromStream(inputStream);
    }

    @Test
    public void shouldThrowOnShortReadOfVersion() throws IOException
    {
        // given
        fillIndex(index);
        final InputStream inputStream = new ShortReadInputStream(writeIndex(index), 3, false);

        expectedException.expect(IOException.class);
        expectedException.expectMessage("Unable to read index snapshot version");

        // when
        readIndex(index, inputStream);
    }

    private void fillIndex(final Long2LongHashIndex index)
    {
        for (int i = 0; i < DATASET_SIZE; i++)
        {
            index.put(i, i);
        }
    }

    private void writeAndReadIndex(final HashIndex index) throws IOException
    {
        final InputStream inputStream = writeIndex(index);

        index.clear();

        readIndex(index, inputStream);
    }

    private InputStream writeIndex(final HashIndex index) throws IOException
    {
        indexSerializer.wrap(index);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        indexSerializer.writeToStream(out);

        return new ByteArrayInputStream(out.toByteArray());
    }

    private void readIndex(final HashIndex index, final InputStream inputStream) throws IOException
    {
        indexSerializer.wrap(index);
        indexSerializer.readFromStream(inputStream);
    }

}
