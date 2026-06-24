/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.utils.net.Address;
import io.netty.channel.Channel;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ChannelPoolTest {
  private static final String MESSAGE_TYPE = "test";
  private final Function<Address, CompletableFuture<Channel>> factory =
      a -> {
        final var channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        return CompletableFuture.completedFuture(channel);
      };
  private final ChannelPool channelPool = new ChannelPool(factory);

  @Test
  void shouldNotUseOldChannelWhenIPChanged() throws UnknownHostException {
    // given
    final Address addressWithOldIP =
        new Address("foo.bar", 1234, InetAddress.getByName("10.1.1.1"));
    final var channelForOldIP = channelPool.getChannel(addressWithOldIP, MESSAGE_TYPE).join();

    // when
    final Address addressWithNewIP =
        new Address("foo.bar", 1234, InetAddress.getByName("10.1.1.2"));
    final var channelForNewIP = channelPool.getChannel(addressWithNewIP, "test").join();

    // then
    assertThat(channelForOldIP).isNotEqualTo(channelForNewIP);
  }

  @Test
  void shouldNotUseIncorrectChannelWhenIPReused() throws UnknownHostException {
    // given
    final Address nodeWithSameIP = new Address("foo.bar", 1234, InetAddress.getByName("10.1.1.1"));
    final var channelForOldNode = channelPool.getChannel(nodeWithSameIP, MESSAGE_TYPE).join();

    // when
    final Address newNodeWithSameIP =
        new Address("foo.foo", 1234, InetAddress.getByName("10.1.1.1"));
    final var channelForNewNode = channelPool.getChannel(newNodeWithSameIP, MESSAGE_TYPE).join();

    // then
    assertThat(channelForOldNode).isNotEqualTo(channelForNewNode);
  }
}
