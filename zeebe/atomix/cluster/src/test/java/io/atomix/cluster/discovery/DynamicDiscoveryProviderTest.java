/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import io.atomix.cluster.Node;
import io.atomix.cluster.NodeId;
import io.atomix.cluster.discovery.NodeDiscoveryEvent.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DynamicDiscoveryProviderTest {

  private final List<DynamicDiscoveryProvider> providers = new ArrayList<>();

  @AfterEach
  void tearDown() throws Exception {
    for (final DynamicDiscoveryProvider provider : providers) {
      provider.leave(null).get(10, TimeUnit.SECONDS);
    }
    providers.clear();
  }

  @Test
  void shouldResolveLocalhostAddress() throws Exception {
    // given
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("localhost:26500"))
            .setRefreshInterval(Duration.ofSeconds(30));
    final DynamicDiscoveryProvider provider = new DynamicDiscoveryProvider(config);
    providers.add(provider);

    // when
    provider.join(null, null).get(10, TimeUnit.SECONDS);

    // then
    Awaitility.await().until(() -> provider.getNodes().size(), is(1));
    final Set<Node> nodes = provider.getNodes();
    assertThat(nodes).isNotEmpty().allMatch(node -> node.address().port() == 26500);
  }

  @Test
  void shouldResolveAddressWithDefaultPort() throws Exception {
    // given
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("localhost"))
            .setRefreshInterval(Duration.ofSeconds(30));
    final DynamicDiscoveryProvider provider = new DynamicDiscoveryProvider(config);
    providers.add(provider);

    // when
    provider.join(null, null).get(10, TimeUnit.SECONDS);

    // then
    Awaitility.await().until(() -> provider.getNodes().size(), is(Matchers.greaterThan(0)));
    assertThat(provider.getNodes())
        // Should use default port 26500
        .allMatch(node -> node.address().port() == 26502);
  }

  @Test
  void shouldResolveMultipleAddresses() throws Exception {
    // given
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("localhost:26500", "127.0.0.1:26501"))
            .setRefreshInterval(Duration.ofSeconds(30));
    final DynamicDiscoveryProvider provider = new DynamicDiscoveryProvider(config);
    providers.add(provider);

    // when
    provider.join(null, null).get(10, TimeUnit.SECONDS);

    // then
    Awaitility.await()
        .untilAsserted(() -> assertThat(provider.getNodes()).hasSizeGreaterThanOrEqualTo(2));
  }

  @Test
  void shouldHandleMultipleResolvedAddress() {
    // given
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("multi.address.test"))
            .setRefreshInterval(Duration.ofSeconds(30));
    final Function<String, List<InetAddress>> mockResolver =
        ignore -> {
          try {
            return List.of(
                InetAddress.getByName("123.45.66.89"), InetAddress.getByName("123.54.76.98"));
          } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
          }
        };
    final DynamicDiscoveryProvider provider = new DynamicDiscoveryProvider(config, mockResolver);

    // when
    provider.join(null, null);
    providers.add(provider);

    // then
    Awaitility.await().until(() -> provider.getNodes().size(), is(2));
    assertThat(provider.getNodes())
        .extracting(node -> node.address().host())
        .containsExactlyInAnyOrder("123.45.66.89", "123.54.76.98");
  }

  @Test
  void shouldCreateNodeWithCorrectId() throws Exception {
    // given
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("127.0.0.1:26500"))
            .setRefreshInterval(Duration.ofSeconds(30));
    final DynamicDiscoveryProvider provider = new DynamicDiscoveryProvider(config);
    providers.add(provider);

    // when
    provider.join(null, null).get(10, TimeUnit.SECONDS);

    // then
    Awaitility.await().until(() -> provider.getNodes().size(), Matchers.equalTo(1));
    final Set<Node> nodes = provider.getNodes();
    assertThat(nodes).first().extracting(Node::id).isEqualTo(NodeId.from("127.0.0.1:26500"));
  }

  @Test
  void shouldFireJoinEventsOnDiscovery() throws Exception {
    // given
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("localhost:26500"))
            .setRefreshInterval(Duration.ofSeconds(30));
    final DynamicDiscoveryProvider provider = new DynamicDiscoveryProvider(config);
    providers.add(provider);

    final CountDownLatch latch = new CountDownLatch(1);
    final List<NodeDiscoveryEvent> events = new ArrayList<>();
    provider.addListener(
        event -> {
          events.add(event);
          latch.countDown();
        });

    // when
    provider.join(null, null).get(10, TimeUnit.SECONDS);

    // then
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(events).isNotEmpty().allMatch(event -> event.type() == Type.JOIN);
  }

  @Test
  void shouldFireLeaveEventsOnNodeRemoval() throws Exception {
    // given
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("localhost:26500"))
            .setRefreshInterval(Duration.ofSeconds(1));

    // A mock resolver that returns an address on the first call and an empty list on subsequent
    // calls to simulate a node leaving
    final AtomicInteger callCount = new AtomicInteger(0);
    final Function<String, List<InetAddress>> mockResolver =
        ignore -> {
          try {
            if (callCount.getAndIncrement() < 1) {
              return List.of(InetAddress.getByName("123.56.78.99"));
            } else {
              return List.of();
            }
          } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
          }
        };

    final DynamicDiscoveryProvider provider = new DynamicDiscoveryProvider(config, mockResolver);
    providers.add(provider);

    final CountDownLatch latch = new CountDownLatch(1);
    final List<NodeDiscoveryEvent> events = new ArrayList<>();
    provider.addListener(
        event -> {
          events.add(event);
          if (event.type() == Type.LEAVE) {
            latch.countDown();
          }
        });

    // when
    provider.join(null, null).get(10, TimeUnit.SECONDS);

    // wait for refresh interval to pass
    Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> provider.getNodes().isEmpty());

    // then
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(events).anyMatch(event -> event.type() == Type.LEAVE);
  }

  @Test
  void shouldNotFailWithUnresolvableAddress() throws Exception {
    // given
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("unresolvable.invalid.host:26500", "localhost:26500"))
            .setRefreshInterval(Duration.ofSeconds(30));

    final Function<String, List<InetAddress>> mockResolver =
        address -> {
          if (address.equals("unresolvable.invalid.host:26500")) {
            throw new RuntimeException("Failed to resolve address");
          } else {
            try {
              return List.of(InetAddress.getByName(address));
            } catch (final UnknownHostException e) {
              throw new RuntimeException(e);
            }
          }
        };

    final DynamicDiscoveryProvider provider = new DynamicDiscoveryProvider(config);
    providers.add(provider);

    // when
    provider.join(null, null).get(10, TimeUnit.SECONDS);

    // then - should still resolve localhost
    Awaitility.await().until(() -> provider.getNodes().size(), is(1));
  }

  @Test
  void shouldUseConfigFromBuilder() {
    // given
    final Duration refreshInterval = Duration.ofMinutes(5);

    // when
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("localhost:26500"))
            .setRefreshInterval(refreshInterval);
    final DynamicDiscoveryProvider provider = new DynamicDiscoveryProvider(config);
    providers.add(provider);

    // then
    assertThat(config.getAddresses()).containsExactly("localhost:26500");
    assertThat(config.getRefreshInterval()).isEqualTo(refreshInterval);
  }

  @Test
  void shouldCreateProviderFromType() {
    // given
    final DynamicDiscoveryConfig config =
        new DynamicDiscoveryConfig()
            .setAddresses(List.of("localhost:26500"))
            .setRefreshInterval(Duration.ofSeconds(30));

    // when
    final NodeDiscoveryProvider provider = DynamicDiscoveryProvider.TYPE.newProvider(config);
    providers.add((DynamicDiscoveryProvider) provider);

    // then
    assertThat(provider).isInstanceOf(DynamicDiscoveryProvider.class);
    assertThat(provider.config()).isEqualTo(config);
  }
}
