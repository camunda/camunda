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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.DnsResolver;

/**
 * A {@link DnsResolver} that rotates the resolved addresses for a host in a round-robin fashion.
 * This effectively distributes HTTP requests across all resolved IP addresses for a given hostname,
 * providing client-side load balancing without requiring an external load balancer.
 *
 * <p>When a hostname resolves to multiple IP addresses (e.g., a DNS round-robin setup, K8s headless
 * service, or Docker Compose), this resolver rotates which address appears first in the returned
 * array. Since the HTTP connection pool typically picks the first address, this achieves
 * round-robin distribution across backends.
 *
 * <p>This resolver is thread-safe.
 */
final class RoundRobinDnsResolver implements DnsResolver {

  private final ConcurrentMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

  @Override
  public InetAddress[] resolve(final String host) throws UnknownHostException {
    final InetAddress[] addresses = InetAddress.getAllByName(host);
    if (addresses.length <= 1) {
      return addresses;
    }

    final AtomicInteger counter = counters.computeIfAbsent(host, k -> new AtomicInteger(0));
    final int index = Math.abs(counter.getAndIncrement() % addresses.length);

    // Rotate the array so that the selected address is first
    final InetAddress[] rotated = new InetAddress[addresses.length];
    for (int i = 0; i < addresses.length; i++) {
      rotated[i] = addresses[(index + i) % addresses.length];
    }
    return rotated;
  }

  @Override
  public String resolveCanonicalHostname(final String host) throws UnknownHostException {
    return InetAddress.getByName(host).getCanonicalHostName();
  }
}
