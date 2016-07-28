package org.camunda.tngp.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.hashindex.IndexKeyMap;
import org.camunda.tngp.hashindex.types.LongKeyHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class LongIndexKeyMapTest
{
    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected IndexKeyMap<LongKeyHandler> map;
    protected static final int MAP_CAPACITY = 100;

    protected LongKeyHandler keyHandler;

    @Before
    public void setUp()
    {
        keyHandler = new LongKeyHandler();
        map = new IndexKeyMap<>(keyHandler, MAP_CAPACITY);
    }

    @Test
    public void shouldReturnDefaultValueForMissingKey()
    {
        keyHandler.theKey = 123L;
        assertThat(map.getAtCurrentKey(-1)).isEqualTo(-1);
    }

    @Test
    public void shouldIncrementNewEntry()
    {
        // when
        keyHandler.theKey = 123L;
        map.incrementAtCurrentKey();

        // then
        assertThat(map.getAtCurrentKey(-1)).isEqualTo(1);

        // when
        map.incrementAtCurrentKey();

        // then
        assertThat(map.getAtCurrentKey(-1)).isEqualTo(2);
    }

    @Test
    public void shouldDecrementNewEntry()
    {
        // given
        keyHandler.theKey = 123L;
        map.incrementAtCurrentKey();
        map.incrementAtCurrentKey();

        // when
        map.decrementAtCurrentKey();

        // then
        assertThat(map.getAtCurrentKey(-1)).isEqualTo(1);
    }

    @Test
    public void shouldRemoveEntryWhenDecrementReachesZero()
    {
        // given
        keyHandler.theKey = 123L;
        map.incrementAtCurrentKey();

        // when
        map.decrementAtCurrentKey();

        // then
        assertThat(map.getAtCurrentKey(-1)).isEqualTo(-1);
    }

    @Test
    public void shouldHandleLargeNumberOfKeys()
    {
        final int[] initialValues = initializeCounters();

        incrementAndAssertAllValues(initialValues);

        decrementAndAssertAllValues(initialValues);
    }

    @Test
    public void shouldReuseSlotAfterRemoval()
    {
        // given
        map = new IndexKeyMap<>(keyHandler, 1);

        keyHandler.theKey = 1L;
        map.incrementAtCurrentKey();
        map.decrementAtCurrentKey();

        // when
        keyHandler.theKey = 2L;
        map.incrementAtCurrentKey();

        // then
        assertThat(map.getAtCurrentKey(-1)).isEqualTo(1);
    }

    @Test
    public void shouldThrowExceptionOnTooManyKeys()
    {
        // given
        map = new IndexKeyMap<>(keyHandler, 1);

        keyHandler.theKey = 1L;
        map.incrementAtCurrentKey();

        keyHandler.theKey = 2L;

        // then
        exception.expect(IndexOutOfBoundsException.class);

        // when
        map.incrementAtCurrentKey();
    }

    protected int[] initializeCounters()
    {
        final int[] values = new int[MAP_CAPACITY];

        for (int i = 0; i < MAP_CAPACITY; i++)
        {
            for (int j = 0; j < 1 + (i % 5); j++)
            {
                keyHandler.theKey = i;
                map.incrementAtCurrentKey();
                values[i] = values[i] + 1;
            }
        }

        return values;
    }

    protected void incrementAndAssertAllValues(int[] currentValues)
    {
        for (int i = 0; i < MAP_CAPACITY; i++)
        {
            keyHandler.theKey = i;
            map.incrementAtCurrentKey();
            assertThat(map.getAtCurrentKey(-1)).isEqualTo(currentValues[i] + 1);
            currentValues[i] = currentValues[i] + 1;
        }
    }

    protected void decrementAndAssertAllValues(int[] currentValues)
    {
        for (int i = 0; i < MAP_CAPACITY; i++)
        {
            keyHandler.theKey = i;
            map.decrementAtCurrentKey();
            assertThat(map.getAtCurrentKey(-1)).isEqualTo(currentValues[i] - 1);
            currentValues[i] = currentValues[i] - 1;
        }
    }
}
