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
package io.zeebe.util.cache;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.agrona.BitUtil.SIZE_OF_CHAR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Test;

public class ExpendableBufferCacheTest {
  private ExpandableBufferCache cache;
  private Map<Long, DirectBuffer> lookupMap = new HashMap<>();

  private int initialBufferCapacity;

  @Before
  public void init() {
    final int capacity = 3;
    initialBufferCapacity = 5 * SIZE_OF_CHAR;

    cache = new ExpandableBufferCache(capacity, initialBufferCapacity, lookupMap::get);

    lookupMap.clear();
  }

  @Test
  public void shouldGet() {
    cache.put(1L, wrapString("foo"));

    assertThat(cache.getSize()).isEqualTo(1);
    assertThat(cache.get(1L)).isEqualTo(wrapString("foo"));
    assertThat(cache.get(2L)).isNull();
  }

  @Test
  public void shouldLookupIfAbsent() {
    lookupMap.put(1L, wrapString("foo"));

    assertThat(cache.getSize()).isEqualTo(0);

    assertThat(cache.get(1L)).isEqualTo(wrapString("foo"));
    assertThat(cache.get(2L)).isNull();
    assertThat(cache.getSize()).isEqualTo(1);
  }

  @Test
  public void shouldPut() {
    cache.put(1L, wrapString("foooo"));

    assertThat(cache.getSize()).isEqualTo(1);
    assertThat(cache.get(1L)).isEqualTo(wrapString("foooo"));

    // override value
    cache.put(1L, wrapString("bar"));

    assertThat(cache.getSize()).isEqualTo(1);
    assertThat(cache.get(1L)).isEqualTo(wrapString("bar"));
  }

  @Test
  public void shouldRemove() {
    cache.put(1L, wrapString("one"));
    cache.put(2L, wrapString("two"));
    cache.put(3L, wrapString("three"));

    cache.remove(2L);

    assertThat(cache.getSize()).isEqualTo(2);

    // insert more values => drop key:1
    cache.put(4L, wrapString("four"));
    cache.put(5L, wrapString("five"));

    assertThat(cache.getSize()).isEqualTo(3);
    assertThat(cache.get(1L)).isNull();
    assertThat(cache.get(2L)).isNull();
    assertThat(cache.get(3L)).isEqualTo(wrapString("three"));
    assertThat(cache.get(4L)).isEqualTo(wrapString("four"));
    assertThat(cache.get(5L)).isEqualTo(wrapString("five"));
  }

  @Test
  public void shouldClear() {
    cache.put(1L, wrapString("foo"));

    cache.clear();

    assertThat(cache.getSize()).isEqualTo(0);
    assertThat(cache.get(1L)).isNull();
  }

  @Test
  public void shouldRemoveLeastRecentlyUsed() {
    // fill cache
    cache.put(1L, wrapString("one"));
    cache.put(2L, wrapString("two"));
    cache.put(3L, wrapString("three"));

    assertThat(cache.getSize()).isEqualTo(3);

    // change the recently used order => keys:2,1,3
    cache.put(1L, wrapString("one-2"));
    cache.get(2L);

    // insert one more value => drop key:3
    cache.put(4L, wrapString("four"));

    assertThat(cache.getSize()).isEqualTo(3);
    assertThat(cache.get(1L)).isEqualTo(wrapString("one-2"));
    assertThat(cache.get(2L)).isEqualTo(wrapString("two"));
    assertThat(cache.get(3L)).isNull();
    assertThat(cache.get(4L)).isEqualTo(wrapString("four"));
  }

  @Test
  public void shouldExpandBuffer() {
    final DirectBuffer bigValue =
        wrapString(String.join("", Collections.nCopies(initialBufferCapacity + 1, "a")));
    cache.put(1L, wrapString("foo")); // < initial buffer capacity
    cache.put(2L, bigValue); // > initial buffer capacity
    cache.put(3L, wrapString("foo")); // < initial buffer capacity
    cache.put(3L, bigValue); // > initial buffer capacity

    assertThat(cache.get(1L)).isEqualTo(wrapString("foo"));
    assertThat(cache.get(2L)).isEqualTo(bigValue);
    assertThat(cache.get(3L)).isEqualTo(bigValue);
  }

  @Test
  public void shouldIgnoreOldDataOfBiggerValues() {
    cache.put(1L, wrapString("foobar")); // initial value
    cache.put(1L, wrapString("elf")); // smaller value

    assertThat(cache.get(1L)).isEqualTo(wrapString("elf"));
  }
}
