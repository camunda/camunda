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
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class BoundedArrayQueueTest {

  @Test
  public void shouldRetrieveAddedElements() {
    // given
    final int numElements = 4;
    final BoundedArrayQueue<Integer> queue = new BoundedArrayQueue<>(numElements);

    for (int i = 0; i < numElements; i++) {
      queue.add(i);
    }

    for (int i = 0; i < numElements; i++) {
      // when
      final Integer queueHead = queue.poll();
      // then
      assertThat(queueHead).isEqualTo(i);
    }
  }

  @Test
  public void shouldRetrieveLessElementsThanAdded() {
    // given
    final int numElements = 3;
    final BoundedArrayQueue<Integer> queue =
        new BoundedArrayQueue<>(numElements); // => queueCapacity becomes next power of two == 4

    for (int i = 0; i < numElements; i++) {
      queue.add(i);
    }

    for (int i = 0; i < numElements; i++) {
      // when
      final Integer queueHead = queue.poll();
      // then
      assertThat(queueHead).isEqualTo(i);
    }
  }
}
