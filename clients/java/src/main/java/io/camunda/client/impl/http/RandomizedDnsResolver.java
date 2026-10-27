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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.hc.client5.http.DnsResolver;

/**
 * A {@link DnsResolver} that shuffles the resolved addresses for a host randomly. This effectively
 * distributes HTTP requests across all resolved IP addresses for a given hostname, providing
 * client-side load balancing without requiring an external load balancer.
 *
 * <p>When a hostname resolves to multiple IP addresses (e.g., a DNS round-robin setup, K8s headless
 * service, or Docker Compose), this resolver shuffles the address order randomly. Since the HTTP
 * connection pool typically picks the first address, this achieves random distribution across
 * backends.
 *
 * <p>This resolver is stateless and thread-safe.
 */
final class RandomizedDnsResolver implements DnsResolver {

  private final DnsLookup dnsLookup;

  RandomizedDnsResolver() {
    this(InetAddress::getAllByName);
  }

  RandomizedDnsResolver(final DnsLookup dnsLookup) {
    this.dnsLookup = dnsLookup;
  }

  @Override
  public InetAddress[] resolve(final String host) throws UnknownHostException {
    final InetAddress[] addresses = dnsLookup.lookup(host);
    if (addresses.length == 0) {
      throw new UnknownHostException(host);
    }
    if (addresses.length == 1) {
      return addresses;
    }

    final InetAddress[] shuffled = addresses.clone();
    final List<InetAddress> addressList = Arrays.asList(shuffled);
    Collections.shuffle(addressList);
    return shuffled;
  }

  @Override
  public String resolveCanonicalHostname(final String host) throws UnknownHostException {
    final InetAddress[] addresses = dnsLookup.lookup(host);
    if (addresses.length == 0) {
      throw new UnknownHostException(host);
    }
    return addresses[0].getCanonicalHostName();
  }

  @FunctionalInterface
  interface DnsLookup {
    InetAddress[] lookup(String host) throws UnknownHostException;
  }
}
