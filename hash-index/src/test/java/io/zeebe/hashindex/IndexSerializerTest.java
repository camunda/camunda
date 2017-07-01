package io.zeebe.hashindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.*;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;
import org.junit.rules.ExpectedException;

public class IndexSerializerTest
{
    private static final int DATASET_SIZE = 100_000;
    private static final int NO_SUCH_KEY = -1;

    Long2LongHashIndex index;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();


    @Before
    public void createIndex() throws Exception
    {
        index = new Long2LongHashIndex(DATASET_SIZE / 16, 16);
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
        for (int i = 0; i < DATASET_SIZE; i++)
        {
            index.put(i, i);
        }

        writeAndReadIndex(index);

        for (int i = 0; i < DATASET_SIZE; i++)
        {
            assertThat(index.get(i, NO_SUCH_KEY)).isEqualTo(i);
        }
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


    private void writeAndReadIndex(Long2LongHashIndex index) throws IOException
    {
        final IndexSerializer indexSerializer = new IndexSerializer();
        indexSerializer.wrap(index);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        indexSerializer.writeToStream(out);

        index.clear();

        final byte[] byteArray = out.toByteArray();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);

        indexSerializer.readFromStream(inputStream);
    }
}
