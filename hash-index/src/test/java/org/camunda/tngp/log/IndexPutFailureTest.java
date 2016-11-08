package org.camunda.tngp.log;

import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class IndexPutFailureTest
{

    protected Long2LongHashIndex index;
    FileChannelIndexStore indexStore;

    @Before
    public void setUp()
    {
        indexStore = FileChannelIndexStore.tempFileIndexStore();
        index = new Long2LongHashIndex(indexStore, 32448, 4 * 1024);
    }

    @After
    public void after()
    {
        indexStore.close();
    }

    @Test
    public void shouldPutElements()
    {
        for (int i = 0; i < 18; i++)
        {
            for (int j = 0; j < 32; j++)
            {
                final long indexPos = i * j;
                index.put(indexPos, 0);
            }
        }
    }
}
