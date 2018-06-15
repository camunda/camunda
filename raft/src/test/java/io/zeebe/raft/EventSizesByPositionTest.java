/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.raft.backpressure.EventSizesByPosition;
import org.junit.Before;
import org.junit.Test;

public class EventSizesByPositionTest
{
    private EventSizesByPosition eventSizesByPosition;

    @Before
    public void init()
    {
        eventSizesByPosition = new EventSizesByPosition();
    }

    @Test
    public void empty()
    {
        assertThat(eventSizesByPosition.isEmpty()).isTrue();
        assertThat(eventSizesByPosition.size()).isEqualTo(0);
    }

    @Test
    public void canAddUpToCapaciy()
    {
        for (int i = 0; i < eventSizesByPosition.getCurrentCapacity(); i++)
        {
            eventSizesByPosition.add(i, i);
        }
    }

    @Test
    public void isAutoGrowing()
    {
        for (int i = 0; i < eventSizesByPosition.getCurrentCapacity(); i++)
        {
            eventSizesByPosition.add(i, i);
        }

        eventSizesByPosition.add(100, 100);
    }

    @Test
    public void canConsumeInOrder()
    {
        for (int i = 0; i < eventSizesByPosition.getCurrentCapacity(); i++)
        {
            eventSizesByPosition.add(i, i);
        }

        for (int i = 0; i < eventSizesByPosition.getCurrentCapacity(); i++)
        {
            assertThat(eventSizesByPosition.markConsumed(i)).isEqualTo(i);
        }
    }

    @Test
    public void canConsumeAfterResize()
    {
        final int initalCapacity = eventSizesByPosition.getCurrentCapacity();
        final int initialCapacityPlusOne = initalCapacity + 1;

        for (int i = 0; i < initalCapacity; i++)
        {
            eventSizesByPosition.add(i, i);
        }

        eventSizesByPosition.add(initialCapacityPlusOne, initialCapacityPlusOne);

        for (int i = 0; i < initalCapacity; i++)
        {
            assertThat(eventSizesByPosition.markConsumed(i)).isEqualTo(i);
        }

        assertThat(eventSizesByPosition.markConsumed(initialCapacityPlusOne)).isEqualTo(initialCapacityPlusOne);
    }

    @Test
    public void isReusing()
    {
        final int initalCapacity = eventSizesByPosition.getCurrentCapacity();

        for (int i = 0; i < initalCapacity; i++)
        {
            eventSizesByPosition.add(i, i);
        }

        for (int i = 0; i < initalCapacity; i++)
        {
            assertThat(eventSizesByPosition.markConsumed(i)).isEqualTo(i);
        }

        for (int i = 0; i < initalCapacity; i++)
        {
            eventSizesByPosition.add(i, i);
        }

        for (int i = 0; i < initalCapacity; i++)
        {
            assertThat(eventSizesByPosition.markConsumed(i)).isEqualTo(i);
        }

        assertThat(eventSizesByPosition.getCurrentCapacity()).isEqualTo(initalCapacity);
    }

    @Test
    public void isReusingAlternating()
    {
        final int initalCapacity = eventSizesByPosition.getCurrentCapacity();

        for (int i = 0; i < 1024; i++)
        {
            eventSizesByPosition.add(i, i);
            assertThat(eventSizesByPosition.markConsumed(i)).isEqualTo(i);
        }

        assertThat(eventSizesByPosition.getCurrentCapacity()).isEqualTo(initalCapacity);
    }

    @Test
    public void doesNotConsumerIfSmaller()
    {
        eventSizesByPosition.add(1, 1);
        assertThat(eventSizesByPosition.markConsumed(0)).isEqualTo(0);
    }

}
