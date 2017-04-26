package org.camunda.tngp.hashindex;

import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.hashindex.types.LongKeyHandler;
import org.camunda.tngp.hashindex.types.LongValueHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 *
 */
public class HashIndexTest
{

    @Mock
    private IndexStore indexStore;

    @Before
    public void init()
    {
        initMocks(this);
    }

    @After
    public void tearDown()
    {
        indexStore.close();
    }

    @Test
    public void shouldRoundToPowerOfTwo()
    {
        // given index not power of two
        final int indexSize = 11;

        // when
        final HashIndex<LongKeyHandler, LongValueHandler> index =
                new HashIndex<>(indexStore,
                LongKeyHandler.class, LongValueHandler.class,
                indexSize, 1, SIZE_OF_LONG, SIZE_OF_LONG);

        // then
        assertThat(index.indexSize).isEqualTo(16);
    }

    @Test
    public void shouldUseLimitPowerOfTwo()
    {
        // given index which is higher than the limit 1 << 27
        final int indexSize = Integer.MAX_VALUE;

        // when
        final HashIndex<LongKeyHandler, LongValueHandler> index =
                new HashIndex<>(indexStore,
                LongKeyHandler.class, LongValueHandler.class,
                indexSize, 1, SIZE_OF_LONG, SIZE_OF_LONG);

        // then
        assertThat(index.indexSize).isEqualTo(1 << 27);
    }

}
