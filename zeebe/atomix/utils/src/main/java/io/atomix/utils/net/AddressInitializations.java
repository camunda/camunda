/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.utils.net;

import io.camunda.zeebe.util.VisibleForTesting;
import io.netty.util.NetUtil;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * This class is split away primarily, so we can test the initialization code, which would otherwise
 * be difficult to do directly.
 */
final class AddressInitializations {

  static InetAddress computeDefaultAdvertisedHost() {
    return computeDefaultAdvertisedHost(
        NetUtil.NETWORK_INTERFACES.stream().flatMap(NetworkInterface::inetAddresses),
        InetAddress::getLocalHost,
        NetUtil.isIpV6AddressesPreferred());
  }

  @VisibleForTesting
  static InetAddress computeDefaultAdvertisedHost(
      final Stream<InetAddress> addresses,
      final LocalHostSupplier localhost,
      final boolean isIPv6Preferred) {
    try {
      return localhost.get();
    } catch (final UnknownHostException e) {
      // ignored - we will compute using heuristics below
    }

    final var nonLoopBackAddresses = collectNonLoopBackAddresses(addresses, isIPv6Preferred);
    final var nonLoopBackAddress = nonLoopBackAddresses.address();

    if (nonLoopBackAddress != null) {
      return nonLoopBackAddress;
    }

    if (isIPv6Preferred) {
      return NetUtil.LOCALHOST6;
    }

    return NetUtil.LOCALHOST4;
  }

  private static NonLoopBackAddresses collectNonLoopBackAddresses(
      final Stream<InetAddress> networkInterfaces, final boolean isIPv6Preferred) {
    // loop through all network interfaces' addresses, and pick the first non loop back IPv4 and
    // IPv6 addresses
    return networkInterfaces
        .filter(Predicate.not(InetAddress::isLoopbackAddress))
        .collect(
            () -> new NonLoopBackAddresses(isIPv6Preferred),
            NonLoopBackAddresses::setIfNonNull,
            (a, b) -> {
              a.setIfNonNull(b.ipv4);
              a.setIfNonNull(b.ipv6);
            });
  }

  private static final class NonLoopBackAddresses {
    private final boolean isIPv6Preferred;

    private InetAddress ipv4;
    private InetAddress ipv6;

    private NonLoopBackAddresses(final boolean isIPv6Preferred) {
      this.isIPv6Preferred = isIPv6Preferred;
    }

    private void setIfNonNull(final InetAddress address) {
      if (NetUtil.isValidIpV6Address(address.getHostAddress())) {
        if (ipv6 == null) {
          ipv6 = address;
        }

        return;
      }

      if (ipv4 == null) {
        ipv4 = address;
      }
    }

    private InetAddress address() {
      if (isIPv6Preferred) {
        return ipv6 != null ? ipv6 : ipv4;
      }

      return ipv4 != null ? ipv4 : ipv6;
    }
  }

  @VisibleForTesting
  @FunctionalInterface
  interface LocalHostSupplier {
    InetAddress get() throws UnknownHostException;
  }
}
