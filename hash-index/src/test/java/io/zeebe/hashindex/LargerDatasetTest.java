package io.zeebe.hashindex;

import org.agrona.BitUtil;
import io.zeebe.hashindex.store.FileChannelIndexStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LargerDatasetTest
{
    private static final int KEYS_TO_PUT = 500_000;

    private static final int BLOCK_LENGTH = 128;

    private static final int INDEX_SIZE = BitUtil.findNextPositivePowerOfTwo(KEYS_TO_PUT / BLOCK_LENGTH);

    protected Long2LongHashIndex index;
    FileChannelIndexStore indexStore;

    @Before
    public void setUp()
    {
        indexStore = FileChannelIndexStore.tempFileIndexStore();
        index = new Long2LongHashIndex(indexStore, INDEX_SIZE, BLOCK_LENGTH);
    }

    @After
    public void after()
    {
        indexStore.close();
    }

    @Test
    public void shouldPutElements()
    {
        for (int i = 0; i < KEYS_TO_PUT; i++)
        {
            index.put(i, 0);
        }
    }
}
