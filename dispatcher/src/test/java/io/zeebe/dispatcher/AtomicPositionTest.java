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
package io.zeebe.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class AtomicPositionTest {
  private final AtomicPosition atomicPosition = new AtomicPosition();

  @Test
  public void shouldGetDefaultPosition() {
    // given

    // when
    final long defaultValue = atomicPosition.get();

    // then
    assertThat(defaultValue).isEqualTo(0);
  }

  @Test
  public void shouldSetAndGetPosition() {
    // given

    // when
    atomicPosition.set(1);

    // then
    assertThat(atomicPosition.get()).isEqualTo(1);
  }

  @Test
  public void shouldProposeMaxOrderedPositionIfNoPositionWasSet() {
    // given

    // when
    final boolean success = atomicPosition.proposeMaxOrdered(1);

    // then
    assertThat(success).isTrue();
    assertThat(atomicPosition.get()).isEqualTo(1);
  }

  @Test
  public void shouldProposeMaxOrderedPosition() {
    // given
    atomicPosition.set(1);

    // when
    final boolean success = atomicPosition.proposeMaxOrdered(2);

    // then
    assertThat(success).isTrue();
    assertThat(atomicPosition.get()).isEqualTo(2);
  }

  @Test
  public void shouldNotProposeMaxOrderedPosition() {
    // given
    atomicPosition.set(2);

    // when
    final boolean success = atomicPosition.proposeMaxOrdered(1);

    // then
    assertThat(success).isFalse();
    assertThat(atomicPosition.get()).isEqualTo(2);
  }

  @Test
  public void shouldResetPosition() {
    // given
    atomicPosition.set(2);

    // when
    atomicPosition.reset();

    // then
    assertThat(atomicPosition.get()).isEqualTo(-1);
  }
}
