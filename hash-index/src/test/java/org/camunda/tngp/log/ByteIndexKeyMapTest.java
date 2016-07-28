package org.camunda.tngp.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Random;

import org.camunda.tngp.hashindex.IndexKeyMap;
import org.camunda.tngp.hashindex.types.ByteArrayKeyHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ByteIndexKeyMapTest
{
    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected IndexKeyMap<ByteArrayKeyHandler> map;
    protected static final int MAP_CAPACITY = 100;

    protected ByteArrayGenerator generator;
    protected ByteArrayKeyHandler keyHandler;

    @Before
    public void setUp()
    {
        keyHandler = new ByteArrayKeyHandler();
        keyHandler.setKeyLength(16);
        map = new IndexKeyMap<>(keyHandler, MAP_CAPACITY);

        generator = new ByteArrayGenerator(16);
    }

    @Test
    public void shouldReturnDefaultValueForMissingKey()
    {
        final byte[] key = generator.next();

        keyHandler.setKey(key);
        assertThat(map.getAtCurrentKey(-1)).isEqualTo(-1);
    }

    @Test
    public void shouldIncrementNewEntry()
    {
        final byte[] key = generator.next();

        // when
        keyHandler.setKey(key);
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
        final byte[] key = generator.next();

        // given
        keyHandler.setKey(key);
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
        final byte[] key = generator.next();

        // given
        keyHandler.setKey(key);
        map.incrementAtCurrentKey();

        // when
        map.decrementAtCurrentKey();

        // then
        assertThat(map.getAtCurrentKey(-1)).isEqualTo(-1);
    }

    @Test
    public void shouldHandleLargeNumberOfKeys()
    {
        final byte[][] keys = generateKeys();
        final int[] initialValues = initializeCounters(keys);

        incrementAndAssertAllValues(keys, initialValues);

        decrementAndAssertAllValues(keys, initialValues);
    }

    @Test
    public void shouldThrowExceptionOnTooManyKeys()
    {
        // given
        map = new IndexKeyMap<>(keyHandler, 1);

        keyHandler.setKey(generator.next());
        map.incrementAtCurrentKey();

        keyHandler.setKey(generator.next());

        // then
        exception.expect(IndexOutOfBoundsException.class);

        // when
        map.incrementAtCurrentKey();
    }

    protected byte[][] generateKeys()
    {
        final byte[][] values = new byte[MAP_CAPACITY][];

        for (int i = 0; i < MAP_CAPACITY; i++)
        {
            values[i] = generator.next();
        }

        return values;
    }

    protected int[] initializeCounters(byte[][] keys)
    {
        final int[] values = new int[MAP_CAPACITY];

        for (int i = 0; i < MAP_CAPACITY; i++)
        {
            final byte[] key = keys[i];
            for (int j = 0; j < 1 + (i % 5); j++)
            {
                keyHandler.setKey(key);
                map.incrementAtCurrentKey();
                values[i] = values[i] + 1;
            }
        }

        return values;
    }

    protected void incrementAndAssertAllValues(byte[][] keys, int[] currentValues)
    {
        for (int i = 0; i < MAP_CAPACITY; i++)
        {
            final byte[] key = keys[i];
            keyHandler.setKey(key);
            map.incrementAtCurrentKey();
            assertThat(map.getAtCurrentKey(-1)).isEqualTo(currentValues[i] + 1);
            currentValues[i] = currentValues[i] + 1;
        }
    }

    protected void decrementAndAssertAllValues(byte[][] keys, int[] currentValues)
    {
        for (int i = 0; i < MAP_CAPACITY; i++)
        {
            final byte[] key = keys[i];
            keyHandler.setKey(key);
            map.decrementAtCurrentKey();
            assertThat(map.getAtCurrentKey(-1)).isEqualTo(currentValues[i] - 1);
            currentValues[i] = currentValues[i] - 1;
        }
    }

    public static class ByteArrayGenerator
    {
        byte[] value;
        Random random = new Random(0L); // always same seed for test reproducability

        public ByteArrayGenerator(int size)
        {
            value = new byte[size];
        }

        byte[] next()
        {
            random.nextBytes(value);
            return Arrays.copyOf(value, value.length);
        }
    }
}
