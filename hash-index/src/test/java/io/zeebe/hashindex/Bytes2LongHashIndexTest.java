package io.zeebe.hashindex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.*;
import org.junit.rules.ExpectedException;

public class Bytes2LongHashIndexTest
{
    static final long MISSING_VALUE = -2;

    byte[][] keys = new byte[16][64];

    Bytes2LongHashIndex index;

    @Rule
    public ExpectedException expection = ExpectedException.none();

    @Before
    public void createIndex()
    {
        final int indexSize = 32;

        index = new Bytes2LongHashIndex(indexSize, 1, 64);

        // generate keys
        for (int i = 0; i < keys.length; i++)
        {
            final byte[] val = String.valueOf(i).getBytes();

            for (int j = 0; j < val.length; j++)
            {
                keys[i][j] = val[j];
            }
        }
    }

    @After
    public void close()
    {
        index.close();
    }

    @Test
    public void shouldFillShorterKeysWithZero()
    {
        // given
        final byte[] key = new byte[64];
        final byte[] shortenedKey = new byte[30];
        System.arraycopy(keys[1], 0, key, 0, 30);
        System.arraycopy(keys[1], 0, shortenedKey, 0, 30);

        index.put(key, 76L);

        // when then
        assertThat(index.get(shortenedKey, -1)).isEqualTo(76L);
    }

    @Test
    public void shouldRejectIfKeyTooLong()
    {
        // then
        expection.expect(IllegalArgumentException.class);

        // when
        index.get(new byte[65], -2);
    }


}
