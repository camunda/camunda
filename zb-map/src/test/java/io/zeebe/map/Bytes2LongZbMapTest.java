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
package io.zeebe.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Random;
import org.junit.*;
import org.junit.rules.ExpectedException;

public class Bytes2LongZbMapTest {

  byte[][] keys = new byte[100_000][64];

  Bytes2LongZbMap map;

  @Rule public ExpectedException expection = ExpectedException.none();

  @Before
  public void createmap() {
    final int tableSize = 32;

    map = new Bytes2LongZbMap(tableSize, 1, 64);

    final Random random = new Random(100);

    // generate keys
    final byte randomBytes[] = new byte[64];
    for (int i = 0; i < keys.length; i++) {
      random.nextBytes(randomBytes);
      keys[i] = randomBytes.clone();
    }
  }

  @After
  public void close() {
    map.close();
  }

  @Test
  public void shouldFillShorterKeysWithZero() {
    // given
    final byte[] key = new byte[64];
    final byte[] shortenedKey = new byte[30];
    System.arraycopy(keys[1], 0, key, 0, 30);
    System.arraycopy(keys[1], 0, shortenedKey, 0, 30);

    map.put(key, 76L);

    // when then
    assertThat(map.get(shortenedKey, -1)).isEqualTo(76L);
  }

  @Test
  public void shouldUpdateValueWithKeyAndRemainingBytesAreZero() {
    // given
    final Bytes2LongZbMap map = new Bytes2LongZbMap(1024 * 32 * 32, 16, 64);
    final byte[] key = new byte[64];
    final byte[] shortenedKey = new byte[30];
    System.arraycopy(keys[1], 0, key, 0, 64);
    System.arraycopy(keys[1], 0, shortenedKey, 0, 30);
    Arrays.fill(key, 30, key.length, (byte) 0);

    for (int i = 0; i < keys.length; i++) {
      map.put(keys[i], i);
    }

    // when
    map.put(shortenedKey, 0x10);
    map.put(key, 0xFF);

    // then
    assertThat(map.get(shortenedKey, -1)).isEqualTo(0xFF);
    assertThat(map.get(key, -1)).isEqualTo(0xFF);
  }

  @Test
  public void shouldGetValueWithKeyAndRemainingBytesAreZero() {
    // given
    final byte[] key = new byte[64];
    final byte[] shortenedKey = new byte[30];
    System.arraycopy(keys[1], 0, key, 0, 64);
    System.arraycopy(keys[1], 0, shortenedKey, 0, 30);

    Arrays.fill(key, 30, key.length, (byte) 0);

    // when
    map.put(key, 76L);

    // then
    assertThat(map.get(shortenedKey, -1)).isEqualTo(76);
  }

  @Test
  public void shouldFailToGetValueWithKeyWhichIsPartOfLargerKey() {
    // given
    final byte[] key = new byte[64];
    final byte[] shortenedKey = new byte[30];
    System.arraycopy(keys[1], 0, key, 0, 64);
    System.arraycopy(keys[1], 0, shortenedKey, 0, 30);

    key[30] = 0;

    // when
    map.put(key, 76L);

    // then
    assertThat(map.get(shortenedKey, -1)).isEqualTo(-1);
  }

  @Test
  public void shouldFailToGetValueWithHalfKey() {
    // given
    final byte[] key = new byte[64];
    final byte[] shortenedKey = new byte[30];
    System.arraycopy(keys[1], 0, key, 0, 64);
    System.arraycopy(keys[1], 0, shortenedKey, 0, 30);

    // when
    map.put(key, 76L);

    // then
    assertThat(map.get(shortenedKey, -1)).isEqualTo(-1);
  }

  @Test
  public void shouldRejectGetIfKeyTooLong() {
    // then
    expection.expect(IllegalArgumentException.class);

    // when
    map.get(new byte[65], -2);
  }

  @Test
  public void shouldRejectPutIfKeyTooLong() {
    // then
    expection.expect(IllegalArgumentException.class);

    // when
    map.put(new byte[65], 2);
  }

  @Test
  public void shouldRejectRemoveIfKeyTooLong() {
    // then
    expection.expect(IllegalArgumentException.class);

    // when
    map.remove(new byte[65], -2);
  }
}
