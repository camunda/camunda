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
package io.zeebe.broker.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import io.zeebe.broker.logstreams.processor.FlatEnumMap;

public class FlatEnumMapTest
{
    private static Object[] elements = new Object[24];
    static
    {
        for (int i = 0; i < elements.length; i++)
        {
            elements[i] = new Object();
        }
    }


    private FlatEnumMap<Object> map;

    @Before
    public void setUp()
    {
        map = new FlatEnumMap<>(Enum1.class, Enum2.class, Enum3.class);
        map.put(Enum1.A, Enum2.A, Enum3.A, elements[0]);
        map.put(Enum1.A, Enum2.A, Enum3.B, elements[1]);
        map.put(Enum1.A, Enum2.A, Enum3.C, elements[2]);
        map.put(Enum1.A, Enum2.A, Enum3.D, elements[3]);

        map.put(Enum1.A, Enum2.B, Enum3.A, elements[4]);
        map.put(Enum1.A, Enum2.B, Enum3.B, elements[5]);
        map.put(Enum1.A, Enum2.B, Enum3.C, elements[6]);
        map.put(Enum1.A, Enum2.B, Enum3.D, elements[7]);

        map.put(Enum1.A, Enum2.C, Enum3.A, elements[8]);
        map.put(Enum1.A, Enum2.C, Enum3.B, elements[9]);
        map.put(Enum1.A, Enum2.C, Enum3.C, elements[10]);
        map.put(Enum1.A, Enum2.C, Enum3.D, elements[11]);

        map.put(Enum1.B, Enum2.A, Enum3.A, elements[12]);
        map.put(Enum1.B, Enum2.A, Enum3.B, elements[13]);
        map.put(Enum1.B, Enum2.A, Enum3.C, elements[14]);
        map.put(Enum1.B, Enum2.A, Enum3.D, elements[15]);

        map.put(Enum1.B, Enum2.B, Enum3.A, elements[16]);
        map.put(Enum1.B, Enum2.B, Enum3.B, elements[17]);
        map.put(Enum1.B, Enum2.B, Enum3.C, elements[18]);
        map.put(Enum1.B, Enum2.B, Enum3.D, elements[19]);

        map.put(Enum1.B, Enum2.C, Enum3.A, elements[20]);
        map.put(Enum1.B, Enum2.C, Enum3.B, elements[21]);
        map.put(Enum1.B, Enum2.C, Enum3.C, elements[22]);
        map.put(Enum1.B, Enum2.C, Enum3.D, elements[23]);
    }

    @Test
    public void shouldAccessElements()
    {
        // then
        assertThat(map.get(Enum1.A, Enum2.A, Enum3.A)).isSameAs(elements[0]);
        assertThat(map.get(Enum1.A, Enum2.A, Enum3.B)).isSameAs(elements[1]);
        assertThat(map.get(Enum1.A, Enum2.A, Enum3.C)).isSameAs(elements[2]);
        assertThat(map.get(Enum1.A, Enum2.A, Enum3.D)).isSameAs(elements[3]);

        assertThat(map.get(Enum1.A, Enum2.B, Enum3.A)).isSameAs(elements[4]);
        assertThat(map.get(Enum1.A, Enum2.B, Enum3.B)).isSameAs(elements[5]);
        assertThat(map.get(Enum1.A, Enum2.B, Enum3.C)).isSameAs(elements[6]);
        assertThat(map.get(Enum1.A, Enum2.B, Enum3.D)).isSameAs(elements[7]);

        assertThat(map.get(Enum1.A, Enum2.C, Enum3.A)).isSameAs(elements[8]);
        assertThat(map.get(Enum1.A, Enum2.C, Enum3.B)).isSameAs(elements[9]);
        assertThat(map.get(Enum1.A, Enum2.C, Enum3.C)).isSameAs(elements[10]);
        assertThat(map.get(Enum1.A, Enum2.C, Enum3.D)).isSameAs(elements[11]);

        assertThat(map.get(Enum1.B, Enum2.A, Enum3.A)).isSameAs(elements[12]);
        assertThat(map.get(Enum1.B, Enum2.A, Enum3.B)).isSameAs(elements[13]);
        assertThat(map.get(Enum1.B, Enum2.A, Enum3.C)).isSameAs(elements[14]);
        assertThat(map.get(Enum1.B, Enum2.A, Enum3.D)).isSameAs(elements[15]);

        assertThat(map.get(Enum1.B, Enum2.B, Enum3.A)).isSameAs(elements[16]);
        assertThat(map.get(Enum1.B, Enum2.B, Enum3.B)).isSameAs(elements[17]);
        assertThat(map.get(Enum1.B, Enum2.B, Enum3.C)).isSameAs(elements[18]);
        assertThat(map.get(Enum1.B, Enum2.B, Enum3.D)).isSameAs(elements[19]);

        assertThat(map.get(Enum1.B, Enum2.C, Enum3.A)).isSameAs(elements[20]);
        assertThat(map.get(Enum1.B, Enum2.C, Enum3.B)).isSameAs(elements[21]);
        assertThat(map.get(Enum1.B, Enum2.C, Enum3.C)).isSameAs(elements[22]);
        assertThat(map.get(Enum1.B, Enum2.C, Enum3.D)).isSameAs(elements[23]);
    }

    @Test
    public void shouldIterateElements()
    {
        // given
        final Set<Object> remainingElements = new HashSet<>(Arrays.asList(elements));
        final Iterator<Object> iterator = map.values();

        // when
        while (iterator.hasNext())
        {
            remainingElements.remove(iterator.next());
        }

        // then
        assertThat(remainingElements).isEmpty();
    }


    enum Enum1
    {
        A,
        B
    }
    enum Enum2
    {
        A,
        B,
        C
    }
    enum Enum3
    {
        A,
        B,
        C,
        D
    }
}
