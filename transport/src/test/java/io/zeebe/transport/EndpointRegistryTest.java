/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.transport.impl.EndpointRegistryImpl;
import io.zeebe.transport.impl.RemoteAddressListImpl;
import org.junit.Before;
import org.junit.Test;

public class EndpointRegistryTest {

  private RemoteAddressList remoteAddressList;
  private EndpointRegistry registry;

  @Before
  public void setUp() {
    remoteAddressList = spy(new RemoteAddressListImpl());
    registry = new EndpointRegistryImpl("test", remoteAddressList);
  }

  @Test
  public void shouldReturnNullIfNotSet() {
    assertThat(registry.getEndpoint(0)).isNull();
  }

  @Test
  public void shouldSetAndGetEndpoint() {
    // given
    final int nodeId = 12;
    final SocketAddress address = new SocketAddress("example", 1234);

    // then
    assertThat(registry.setEndpoint(nodeId, address)).isNull();
    assertThat(registry.getEndpoint(nodeId).getAddress()).isEqualTo(address);

    verify(remoteAddressList).register(address);
  }

  @Test
  public void shouldReplaceEndpoint() {
    // given
    final int nodeId = 12;
    final SocketAddress firstAddress = new SocketAddress("example", 1234);
    final SocketAddress secondAddress = new SocketAddress("example2", 5678);
    registry.setEndpoint(nodeId, firstAddress);

    // then
    assertThat(registry.setEndpoint(nodeId, secondAddress)).isEqualTo(firstAddress);

    verify(remoteAddressList).register(secondAddress);
    verify(remoteAddressList)
        .deactivate(argThat(remoteAddress -> firstAddress.equals(remoteAddress.getAddress())));
  }

  @Test
  public void shouldIgnoreSettingSameEndpoint() {
    // given
    final int nodeId = 12;
    final SocketAddress address = new SocketAddress("example", 1234);
    registry.setEndpoint(nodeId, address);

    // then
    assertThat(registry.setEndpoint(nodeId, address)).isNull();

    verify(remoteAddressList, times(2)).register(address);
    verify(remoteAddressList, never())
        .deactivate(argThat(remoteAddress -> address.equals(remoteAddress.getAddress())));
  }

  @Test
  public void shouldRemoveEndpoint() {
    // given
    final int nodeId = 12;
    final SocketAddress address = new SocketAddress("example", 1234);
    registry.setEndpoint(nodeId, address);

    // then
    assertThat(registry.removeEndpoint(nodeId)).isEqualTo(address);

    verify(remoteAddressList).register(address);
    verify(remoteAddressList)
        .deactivate(argThat(remoteAddress -> address.equals(remoteAddress.getAddress())));
  }

  @Test
  public void shouldIgnoreRemoveOfNonExistingEndpoint() {
    assertThat(registry.removeEndpoint(13)).isNull();
    verify(remoteAddressList, never()).deactivate(any());
  }
}
