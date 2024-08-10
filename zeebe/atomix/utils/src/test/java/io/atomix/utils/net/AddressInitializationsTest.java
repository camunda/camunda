/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.utils.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.utils.net.AddressInitializations.LocalHostSupplier;
import io.netty.util.NetUtil;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class AddressInitializationsTest {
  @Test
  void shouldUseLocalHostName() {
    // given - where the fake local host will be the ANY address (e.g. 0.0.0.0 for IPv4)
    final var fakeLocalHost = new InetSocketAddress(0).getAddress();
    final var addresses = addresses();

    // when
    final var address =
        AddressInitializations.computeDefaultAdvertisedHost(addresses, () -> fakeLocalHost, false);

    // then
    assertThat(address).isEqualTo(fakeLocalHost);
  }

  @Test
  void shouldPreferIPv4AdvertisedHost() {
    // given
    final var addresses = addresses();

    // when
    final var address =
        AddressInitializations.computeDefaultAdvertisedHost(
            addresses, nonResolvableLocalhost(), false);

    // then
    assertThat(address).isEqualTo(NetUtil.createInetAddressFromIpAddressString("192.168.0.1"));
  }

  @Test
  void shouldPreferIPv6AdvertisedHost() {
    // given
    final var addresses = addresses();

    // when
    final var address =
        AddressInitializations.computeDefaultAdvertisedHost(
            addresses, nonResolvableLocalhost(), true);

    // then
    assertThat(address)
        .isEqualTo(
            NetUtil.createInetAddressFromIpAddressString("2001:9e8:1997:d300:cac3:942a:cab9:f48e"));
  }

  @Test
  void shouldFallbackToLoopBackAdvertisedHostIPv4() {
    // given
    final var addresses = loopBackAddresses();

    // when
    final var address =
        AddressInitializations.computeDefaultAdvertisedHost(
            addresses, nonResolvableLocalhost(), false);

    // then
    assertThat(address).isEqualTo(NetUtil.LOCALHOST4);
  }

  @Test
  void shouldFallbackToLoopBackAdvertisedHostIPv6() {
    // given
    final var addresses = loopBackAddresses();

    // when
    final var address =
        AddressInitializations.computeDefaultAdvertisedHost(
            addresses, nonResolvableLocalhost(), true);

    // then
    assertThat(address).isEqualTo(NetUtil.LOCALHOST6);
  }

  private Stream<InetAddress> addresses() {
    final var ipv4 = NetUtil.createInetAddressFromIpAddressString("192.168.0.1");
    final var ipv6 =
        NetUtil.createInetAddressFromIpAddressString("2001:9e8:1997:d300:cac3:942a:cab9:f48e");

    return Stream.concat(loopBackAddresses(), Stream.of(ipv4, ipv6));
  }

  private Stream<InetAddress> loopBackAddresses() {
    return Stream.of(NetUtil.LOCALHOST4, NetUtil.LOCALHOST6);
  }

  private LocalHostSupplier nonResolvableLocalhost() {
    return () -> InetAddress.getByName("please.make.sure.i.do.not.exist.ok?");
  }
}
