/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.processor;

import java.util.Iterator;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class FlatEnumMap<V>
{
    private final Object[] elements;

    private final int enum2Cardinality;
    private final int enum3Cardinality;

    private final ValueIterator valueIt = new ValueIterator();

    public <R extends Enum<R>, S extends Enum<S>, T extends Enum<T>> FlatEnumMap(
            Class<R> enum1,
            Class<S> enum2,
            Class<T> enum3)
    {

        this.enum2Cardinality = enum2.getEnumConstants().length;
        this.enum3Cardinality = enum3.getEnumConstants().length;

        final int cardinality =
                enum1.getEnumConstants().length
                * enum2Cardinality
                * enum3Cardinality;
        this.elements = new Object[cardinality];
    }

    public V get(Enum key1, Enum key2, Enum key3)
    {
        final int index = mapToIndex(key1, key2, key3);
        return (V) elements[index];
    }

    public void put(Enum key1, Enum key2, Enum key3, V value)
    {
        final int index = mapToIndex(key1, key2, key3);
        if (elements[index] != null)
        {
            throw new RuntimeException("test - overwrite detected");
        }
        elements[index] = value;
    }

    public boolean containsKey(Enum key1, Enum key2, Enum key3)
    {
        final int index = mapToIndex(key1, key2, key3);
        return elements[index] != null;
    }

    private int mapToIndex(Enum key1, Enum key2, Enum key3)
    {
        return (key1.ordinal() * enum2Cardinality * enum3Cardinality) + (key2.ordinal() * enum3Cardinality) + key3.ordinal();
    }

    /**
     * BEWARE: does not detect concurrent modifications and behaves incorrectly in this case
     */
    public Iterator<V> values()
    {
        valueIt.init();
        return valueIt;
    }

    private class ValueIterator implements Iterator<V>
    {
        private int next;

        private void scanToNext()
        {
            do
            {
                next++;
            }
            while (next < elements.length && elements[next] == null);
        }

        public void init()
        {
            next = -1;
            scanToNext();
        }

        @Override
        public boolean hasNext()
        {
            return next < elements.length;
        }

        @Override
        public V next()
        {
            final V element = (V) elements[next];
            scanToNext();
            return element;
        }

    }
}
