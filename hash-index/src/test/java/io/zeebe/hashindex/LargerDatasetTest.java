package io.zeebe.hashindex;

import java.io.IOException;

import org.agrona.BitUtil;
import org.junit.*;
import org.junit.rules.ExpectedException;

public class LargerDatasetTest
{
    private static final int KEYS_TO_PUT = 1_000_000;

    private static final int RECORDS_PER_BLOCK = 16;

    private static final int INDEX_SIZE = BitUtil.findNextPositivePowerOfTwo(KEYS_TO_PUT / RECORDS_PER_BLOCK);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Long2LongHashIndex index;

    @Before
    public void setUp() throws IOException
    {
        index = new Long2LongHashIndex(INDEX_SIZE, RECORDS_PER_BLOCK);
    }

    @After
    public void after()
    {
        index.close();
    }

    @Test
    public void shouldPutElements()
    {
        for (int i = 0; i < KEYS_TO_PUT; i++)
        {
            index.put(i, i);
        }

        for (int i = 0; i < KEYS_TO_PUT; i++)
        {
            if (index.get(i, -1) != i)
            {
                throw new RuntimeException("Illegal value for " + i);
            }
        }

    }
}
