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

class RandomizedDnsResolverTest {

  private static final InetAddress ADDR_1;
  private static final InetAddress ADDR_2;
  private static final InetAddress ADDR_3;

  static {
    try {
      ADDR_1 = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
      ADDR_2 = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});
      ADDR_3 = InetAddress.getByAddress(new byte[] {10, 0, 0, 3});
    } catch (final UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void shouldReturnSingleAddressUnchanged() throws UnknownHostException {
    // given — resolver with a single address
    final InetAddress[] singleAddress = {ADDR_1};
    final RandomizedDnsResolver resolver = new RandomizedDnsResolver(host -> singleAddress);

    // when
    final InetAddress[] result = resolver.resolve("any-host");

    // then
    assertThat(result).hasSize(1);
    assertThat(result[0]).isEqualTo(ADDR_1);
  }

  @Test
  void shouldReturnAllAddresses() throws UnknownHostException {
    // given — resolver with multiple addresses
    final InetAddress[] addresses = {ADDR_1, ADDR_2, ADDR_3};
    final RandomizedDnsResolver resolver = new RandomizedDnsResolver(host -> addresses);

    // when
    final InetAddress[] result = resolver.resolve("any-host");

    // then — all addresses are returned
    assertThat(result).hasSize(3);
    // result is a new shuffled array, not the original reference
    assertThat(result).isNotSameAs(addresses);
    assertThat(result).containsExactlyInAnyOrder(ADDR_1, ADDR_2, ADDR_3);
  }
}
