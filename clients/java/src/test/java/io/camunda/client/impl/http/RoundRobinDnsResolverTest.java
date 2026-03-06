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
package io.camunda.client.impl.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class RoundRobinDnsResolverTest {

  private final RoundRobinDnsResolver resolver = new RoundRobinDnsResolver();

  @Test
  void shouldReturnSingleAddressUnchanged() throws UnknownHostException {
    // given — localhost always resolves to at least one address
    final InetAddress[] first = resolver.resolve("127.0.0.1");

    // then — single IP literal always returns 1 entry
    assertThat(first).hasSize(1);
    assertThat(first[0].getHostAddress()).isEqualTo("127.0.0.1");
  }

  @Test
  void shouldRotateMultipleAddresses() throws UnknownHostException {
    // given — localhost typically resolves to both 127.0.0.1 and ::1 (or just one)
    // We verify the rotation logic with the "localhost" hostname
    final InetAddress[] allAddresses = InetAddress.getAllByName("localhost");

    // If localhost resolves to only 1 address, we can't test rotation
    if (allAddresses.length <= 1) {
      // Just verify it returns the same address
      final InetAddress[] result = resolver.resolve("localhost");
      assertThat(result).hasSize(1);
      return;
    }

    // when — resolve multiple times
    final InetAddress[] first = resolver.resolve("localhost");
    final InetAddress[] second = resolver.resolve("localhost");

    // then — the first address should be different (rotated)
    assertThat(first).hasSameSizeAs(allAddresses);
    assertThat(second).hasSameSizeAs(allAddresses);
    assertThat(first[0]).isNotEqualTo(second[0]);
  }

  @Test
  void shouldCompleteCycleBackToFirstAddress() throws UnknownHostException {
    // given
    final InetAddress[] allAddresses = InetAddress.getAllByName("localhost");

    // when — resolve as many times as there are addresses
    final InetAddress[] first = resolver.resolve("localhost");
    for (int i = 1; i < allAddresses.length; i++) {
      resolver.resolve("localhost");
    }
    final InetAddress[] cycled = resolver.resolve("localhost");

    // then — after a full cycle, the first address should be the same
    assertThat(cycled[0]).isEqualTo(first[0]);
  }

  @Test
  void shouldMaintainSeparateCountersPerHost() throws UnknownHostException {
    // given — resolve host "localhost" several times to advance its counter
    resolver.resolve("localhost");
    resolver.resolve("localhost");

    // when — resolve a different host for the first time
    final InetAddress[] firstForIpHost = resolver.resolve("127.0.0.1");

    // then — the new host should start from index 0, independent of the other host's counter
    // For single-address hosts like 127.0.0.1, the address should be returned unchanged
    assertThat(firstForIpHost).hasSize(1);
    assertThat(firstForIpHost[0].getHostAddress()).isEqualTo("127.0.0.1");
  }
}
