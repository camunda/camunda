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
package io.zeebe.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class SocketAddressTest {

  @Test
  public void shouldBeEqual() {
    // given
    final SocketAddress foo = new SocketAddress("127.0.0.1", 123);
    final SocketAddress bar = new SocketAddress("127.0.0.1", 123);

    // then
    assertThat(foo).isEqualTo(bar);
    assertThat(foo).isEqualByComparingTo(bar);
    assertThat(foo.hashCode()).isEqualTo(bar.hashCode());
  }

  @Test
  public void shouldNotBeEqual() {
    // given
    final SocketAddress foo = new SocketAddress("127.0.0.1", 123);
    final SocketAddress bar = new SocketAddress("127.0.0.2", 123);

    // then
    assertThat(foo).isNotEqualTo(bar);
    assertThat(foo).isNotEqualByComparingTo(bar);
    assertThat(foo.hashCode()).isNotEqualTo(bar.hashCode());
  }

  @Test
  public void shouldNotBeEqualAfterSingleReset() {
    // given
    final SocketAddress foo = new SocketAddress("127.0.0.1", 0);
    final SocketAddress bar = new SocketAddress("127.0.0.1", 0);

    // when
    foo.reset();

    // then
    assertThat(foo).isNotEqualTo(bar);
    assertThat(foo).isNotEqualByComparingTo(bar);
    assertThat(foo.hashCode()).isNotEqualTo(bar.hashCode());
  }

  @Test
  public void shouldBeEqualAfterReset() {
    // given
    final SocketAddress foo = new SocketAddress("127.0.0.1", 123);
    final SocketAddress bar = new SocketAddress("192.168.0.1", 456);

    // when
    foo.reset();
    bar.reset();

    // then
    assertThat(foo).isEqualTo(bar);
    assertThat(foo).isEqualByComparingTo(bar);
    assertThat(foo.hashCode()).isEqualTo(bar.hashCode());
  }
}
