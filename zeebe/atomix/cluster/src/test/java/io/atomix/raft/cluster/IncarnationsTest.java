/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;

final class IncarnationsTest {

  @Test
  void shouldNeverGenerateReservedIncarnations() {
    // given - a random source that first returns the reserved values
    final var values = new ConcurrentLinkedQueue<>(List.of(0L, Long.MIN_VALUE, 0L, 42L));

    // when
    final long incarnation = Incarnations.newIncarnation(values::remove);

    // then
    assertThat(incarnation).isEqualTo(42L);
  }
}
