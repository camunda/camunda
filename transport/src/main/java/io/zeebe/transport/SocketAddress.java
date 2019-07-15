/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import static io.zeebe.util.StringUtil.fromBytes;
import static io.zeebe.util.StringUtil.getBytes;

import java.net.InetSocketAddress;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/*
 * Has the same purpose as InetSocketAddress. However, we can't use that because InetSocketAddress is immutable
 * which is not practical in a garbage-free environment.
 */
public class SocketAddress implements Comparable<SocketAddress> {

  public static final int MAX_HOST_LENGTH = 128;

  protected final byte[] byteArray;
  protected final UnsafeBuffer hostBuffer;
  protected int port;

  public SocketAddress() {
    byteArray = new byte[MAX_HOST_LENGTH];
    this.hostBuffer = new UnsafeBuffer(byteArray, 0, 0);
  }

  public SocketAddress(String host, int port) {
    this();
    host(host);
    port(port);
  }

  public SocketAddress(InetSocketAddress address) {
    this(address.getHostName(), address.getPort());
  }

  public SocketAddress(SocketAddress other) {
    this();
    host(other.hostBuffer, 0, other.hostLength());
    port(other.port);
  }

  public MutableDirectBuffer getHostBuffer() {
    return hostBuffer;
  }

  public SocketAddress host(final DirectBuffer src, int offset, final int length) {
    checkHostLength(length);
    hostLength(length);
    hostBuffer.putBytes(0, src, offset, length);
    return this;
  }

  public SocketAddress host(final byte[] src, final int offset, final int length) {
    checkHostLength(length);
    hostLength(length);
    hostBuffer.putBytes(0, src, offset, length);
    return this;
  }

  public SocketAddress host(final String host) {
    final byte[] hostBytes = getBytes(host);
    return host(hostBytes, 0, hostBytes.length);
  }

  // required for object mapper deserialization
  public SocketAddress setHost(final String host) {
    return host(host);
  }

  public String host() {
    final int hostLength = hostLength();
    final byte[] tmp = new byte[hostLength];
    hostBuffer.getBytes(0, tmp, 0, hostLength);

    return fromBytes(tmp);
  }

  public int hostLength() {
    return hostBuffer.capacity();
  }

  public SocketAddress hostLength(final int hostLength) {
    hostBuffer.wrap(byteArray, 0, hostLength);
    return this;
  }

  protected void checkHostLength(final int hostLength) {
    if (hostLength > MAX_HOST_LENGTH) {
      throw new RuntimeException(
          String.format(
              "Host length exceeds max length (%d > %d bytes)", hostLength, MAX_HOST_LENGTH));
    }
  }

  public int port() {
    return port;
  }

  public SocketAddress port(final int port) {
    this.port = port;
    return this;
  }

  // required for object mapper deserialization
  public SocketAddress setPort(final int port) {
    return port(port);
  }

  public SocketAddress reset() {
    port = 0;
    hostLength(0);

    return this;
  }

  public void wrap(final SocketAddress endpoint) {
    reset();
    host(endpoint.getHostBuffer(), 0, endpoint.hostLength());
    port(endpoint.port());
  }

  public InetSocketAddress toInetSocketAddress() {
    return new InetSocketAddress(host(), port);
  }

  @Override
  public int compareTo(final SocketAddress o) {
    final DirectBuffer thisHostBuffer = getHostBuffer();
    final DirectBuffer thatHostBuffer = o.getHostBuffer();

    int cmp = thisHostBuffer.compareTo(thatHostBuffer);

    if (cmp == 0) {
      cmp = Integer.compare(port(), o.port());
    }

    return cmp;
  }

  @Override
  public int hashCode() {
    int result = hostBuffer.hashCode();
    result = 31 * result + port;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final SocketAddress that = (SocketAddress) o;

    if (port != that.port) {
      return false;
    }
    return hostBuffer.equals(that.hostBuffer);
  }

  @Override
  public String toString() {
    return host() + ":" + port();
  }

  /**
   * Tries to parse a address string to create a new socket address.
   *
   * @param address the address string with format host:port
   * @return the created socket address
   * @throws IllegalArgumentException if the address cannot be parsed
   */
  public static SocketAddress from(final String address) {
    final String[] parts = address.split(":", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException(
          "Address has to be in format host:port but was: " + address);
    }

    final int port;

    final String portString = parts[1];
    try {
      port = Integer.parseInt(portString);
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException(
          "Port of address '" + address + "' has to be a number but was: " + portString);
    }

    return new SocketAddress(parts[0], port);
  }
}
