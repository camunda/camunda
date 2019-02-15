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
package io.zeebe.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class IntListIteratorTest {

  private static final List<Integer> NUMBERS = new ArrayList<>();

  static {
    NUMBERS.add(1);
    NUMBERS.add(2);
    NUMBERS.add(3);
  }

  @Test
  public void shouldIterateArrayListBoxed() {
    // when
    final IntListIterator iterator = new IntListIterator(NUMBERS);

    // then
    assertThat(iterator).toIterable().containsExactly(1, 2, 3);
  }

  @Test
  public void shouldIterateArrayListPrimitive() {
    // when
    final IntListIterator iterator = new IntListIterator(NUMBERS);

    // then
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.nextInt()).isEqualTo(1);
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.nextInt()).isEqualTo(2);
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.nextInt()).isEqualTo(3);
    assertThat(iterator.hasNext()).isFalse();
  }
}
