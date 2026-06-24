/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.utils.net;

import com.google.common.net.HostAndPort;
import io.netty.util.NetUtil;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/** Representation of a network address. */
public final class Address {
  private static final int DEFAULT_PORT = 5679;
  private static final InetAddress DEFAULT_ADVERTISED_HOST =
      AddressInitializations.computeDefaultAdvertisedHost();

  private final String host;
  private final int port;
  private volatile InetSocketAddress socketAddress;

  public Address(final String host, final int port) {
    this(host, port, null);
  }

  public Address(final String host, final int port, final InetAddress address) {
    this.host = host;
    this.port = port;
    if (address != null) {
      socketAddress = new InetSocketAddress(address, port);
    } else {
      socketAddress = InetSocketAddress.createUnresolved(host, port);
    }
  }

  public Address(final InetSocketAddress address) {
    host = address.getHostName();
    port = address.getPort();
    socketAddress = address;
  }

  /**
   * Returns an address that binds to all interfaces.
   *
   * @return the address
   */
  public static Address local() {
    return from(DEFAULT_PORT);
  }

  /**
   * Returns the address from the given host:port string.
   *
   * @param address the address string
   * @return the address
   */
  public static Address from(final String address) {
    try {
      final HostAndPort parsedAddress =
          HostAndPort.fromString(address).withDefaultPort(DEFAULT_PORT);
      return new Address(parsedAddress.getHost(), parsedAddress.getPort());
    } catch (final IllegalStateException e) {
      return from(DEFAULT_PORT);
    }
  }

  /**
   * Returns an address for the given host/port.
   *
   * @param host the host name
   * @param port the port
   * @return a new address
   */
  public static Address from(final String host, final int port) {
    return new Address(host, port);
  }

  /**
   * Returns an address for the local host and the given port.
   *
   * @param port the port
   * @return a new address
   */
  public static Address from(final int port) {
    try {
      final InetAddress address = getLocalAddress();
      return new Address(address.getHostName(), port);
    } catch (final UnknownHostException e) {
      throw new IllegalArgumentException("Failed to locate host", e);
    }
  }

  /**
   * Returns the default host for this machine that should be reachable externally. We do this by
   * first looking up the hostname of the machine and resolving its DNS name to an IP address. If
   * that fails, then we use the following heuristic:
   *
   * <ol>
   *   <li>Collect the first non-loopback IPv4 and IPv6 addresses
   *   <li>If IPv4 is preferred, return the IPv4 one; if there is none, the IPv6 one
   *   <li>If IPv6 is preferred, return the IPv6 one; if there is none, the IPv4 one
   *   <li>If there were no non-loopback addresses, return the appropriate loopback host
   * </ol>
   */
  public static InetAddress defaultAdvertisedHost() {
    return DEFAULT_ADVERTISED_HOST;
  }

  /** Returns the local host. */
  private static InetAddress getLocalAddress() throws UnknownHostException {
    try {
      return InetAddress.getLocalHost(); // first NIC
    } catch (final Exception ignore) {
      return NetUtil.LOCALHOST;
    }
  }

  /**
   * Returns the host name.
   *
   * @return the host name
   */
  public String host() {
    return host;
  }

  /**
   * Returns the port.
   *
   * @return the port
   */
  public int port() {
    return port;
  }

  /**
   * Tries to resolve and returns the IP address.
   *
   * @return the IP address
   */
  public InetAddress tryResolveAddress() {
    if (socketAddress.isUnresolved()) {
      // the constructor will by default attempt to resolve the host, and will fall back to the
      // unresolved address if it couldn't
      socketAddress = new InetSocketAddress(host, port);
      return socketAddress.getAddress();
    }

    return socketAddress.getAddress();
  }

  /**
   * Returns the IP address or null if it is unresolved.
   *
   * @return the IP address
   */
  public InetAddress getAddress() {
    return socketAddress.getAddress();
  }

  /**
   * Returns the socket address.
   *
   * @return the socket address
   */
  public InetSocketAddress socketAddress() {
    return socketAddress;
  }

  @Override
  public int hashCode() {
    return Objects.hash(host, port);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Address that = (Address) obj;
    return host.equals(that.host) && port == that.port;
  }

  @Override
  public String toString() {
    final String host = host();
    final int port = port();
    if (host.matches("([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}")) {
      return String.format("[%s]:%d", host, port);
    } else {
      return String.format("%s:%d", host, port);
    }
  }

  /** Address type. */
  public enum Type {
    IPV4,
    IPV6,
  }
}
