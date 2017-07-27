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
package io.zeebe.map;

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

public class ZbMapSerializerTest
{
    private static final int DATASET_SIZE = 100_000;
    private static final int NO_SUCH_KEY = -1;

    private Long2LongZbMap map;
    private ZbMapSerializer mapSerializer;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();


    @Before
    public void createmap() throws Exception
    {
        map = new Long2LongZbMap(DATASET_SIZE / 16, 16);
        mapSerializer = new ZbMapSerializer();
    }

    @After
    public void close()
    {
        map.close();
    }

    @Test
    public void shouldRestoreEmptymap() throws IOException
    {
        writeAndReadmap(map);

        for (int i = 0; i < DATASET_SIZE; i++)
        {
            assertThat(map.get(i, NO_SUCH_KEY)).isEqualTo(NO_SUCH_KEY);
        }
    }

    @Test
    public void shouldRestoreFullmap() throws IOException
    {
        fillmap(map);

        writeAndReadmap(map);

//        assertThat(map.get(8189, -1)).isEqualTo(8189);
        for (int i = 0; i < DATASET_SIZE; i++)
        {
            assertThat(map.get(i, NO_SUCH_KEY)).isEqualTo(i);
        }
    }

    @Test
    public void shouldRestoreFullmapWithTemporaryReadErrors() throws IOException
    {
        // given
        fillmap(map);
        final InputStream inputStream = writemap(map);
        map.clear();

        // when
        readmap(map, new RepeatedlyFailingInputStream(inputStream));

        // then
        for (int i = 0; i < DATASET_SIZE; i++)
        {
            assertThat(map.get(i, NO_SUCH_KEY)).isEqualTo(i);
        }
    }

    @Test
    public void shouldThrowOnShortReadOfmapBuffer() throws IOException
    {
        // given
        fillmap(map);
        final InputStream inputStream = new ShortReadInputStream(writemap(map), 1024, false);

        expectedException.expect(IOException.class);
        expectedException.expectMessage("Unable to read full map buffer from input stream. " +
            "Only read 1020 bytes but expected 65536 bytes.");

        // when
        readmap(map, inputStream);
    }

    @Test
    public void shouldThrowOnFailedReadOfmapBuffer() throws IOException
    {
        // given
        fillmap(map);
        final InputStream inputStream = new ShortReadInputStream(writemap(map), 1025, true);

        expectedException.expect(IOException.class);
        expectedException.expectMessage("Read failure");

        // when
        readmap(map, inputStream);
    }

    @Test
    public void shouldThrowOnIllegalVersion() throws IOException
    {
        final ZbMapSerializer mapSerializer = new ZbMapSerializer();
        mapSerializer.wrap(map);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        mapSerializer.writeToStream(out);

        map.clear();

        final byte[] byteArray = out.toByteArray();

        // set illegal version
        new UnsafeBuffer(byteArray).putInt(0, 1001);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);

        expectedException.expect(RuntimeException.class);
        mapSerializer.readFromStream(inputStream);
    }

    @Test
    public void shouldThrowOnShortReadOfVersion() throws IOException
    {
        // given
        fillmap(map);
        final InputStream inputStream = new ShortReadInputStream(writemap(map), 3, false);

        expectedException.expect(IOException.class);
        expectedException.expectMessage("Unable to read map snapshot version");

        // when
        readmap(map, inputStream);
    }

    private void fillmap(final Long2LongZbMap map)
    {
        for (int i = 0; i < DATASET_SIZE; i++)
        {
            map.put(i, i);
        }
    }

    private void writeAndReadmap(final ZbMap map) throws IOException
    {
        final long sizeBefore = map.size();
        final InputStream inputStream = writemap(map);

        map.clear();

        readmap(map, inputStream);
        final long sizeAfter = map.size();
        assertThat(sizeBefore).isEqualTo(sizeAfter);
    }

    private InputStream writemap(final ZbMap map) throws IOException
    {
        mapSerializer.wrap(map);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        mapSerializer.writeToStream(out);
        assertThat((long) out.toByteArray().length).isEqualTo(mapSerializer.serializationSize());

        return new ByteArrayInputStream(out.toByteArray());
    }

    private void readmap(final ZbMap map, final InputStream inputStream) throws IOException
    {
        mapSerializer.wrap(map);
        mapSerializer.readFromStream(inputStream);

    }

}
