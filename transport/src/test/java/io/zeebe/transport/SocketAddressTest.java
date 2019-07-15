/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
