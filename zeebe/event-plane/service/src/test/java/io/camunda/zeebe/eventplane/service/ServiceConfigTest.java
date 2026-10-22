/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.store.StoreConfig;
import io.camunda.zeebe.eventplane.store.StoreConfig.Type;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ServiceConfigTest {

  @Test
  void shouldDefaultToNoPeersAKeepAliveOf15SecondsAndAnEmptyObjectOfCredentials() {
    // when
    final var config = new ServiceConfig(new InetSocketAddress("127.0.0.1", 0));

    // then
    assertThat(config.peers()).isEmpty();
    assertThat(config.keepAlive()).isEqualTo(Duration.ofSeconds(15));
    assertThat(config.credentialsJson()).isEqualTo("{}");
  }

  @Test
  void shouldDefaultToNoClusters() {
    // when
    final var config = new ServiceConfig(new InetSocketAddress("127.0.0.1", 0));

    // then
    assertThat(config.clusters()).isEmpty();
  }

  @Test
  void shouldKeepACopyOfThePeers() {
    // given
    final var peers = new ArrayList<>(List.of(URI.create("http://a:1")));
    final var config = new ServiceConfig(new InetSocketAddress(0)).withPeers(peers);

    // when
    peers.add(URI.create("http://b:2"));

    // then
    assertThat(config.peers()).containsExactly(URI.create("http://a:1"));
  }

  @Test
  void shouldNotShowTheCredentials() {
    // given
    final var config =
        new ServiceConfig(new InetSocketAddress(0)).withCredentialsJson("{\"secret\":\"s3cr3t\"}");

    // when, then
    assertThat(config.toString()).doesNotContain("s3cr3t").contains("keepAlive=PT15S");
  }

  @Test
  void shouldKeepASortedCopyOfTheClusters() {
    // given
    final var clusters = new HashMap<String, StoreConfig>();
    clusters.put("west", StoreConfig.filesystem(Path.of("/west")));
    clusters.put("east", StoreConfig.filesystem(Path.of("/east")));
    final var config = new ServiceConfig(new InetSocketAddress(0)).withClusters(clusters);

    // when
    clusters.put("north", StoreConfig.filesystem(Path.of("/north")));

    // then
    assertThat(config.clusters()).containsOnlyKeys("east", "west");
    assertThat(config.clusters().keySet()).containsExactly("east", "west");
  }

  @Test
  void shouldShowTheClusterIdsButNotTheStores() {
    // given
    final var store = new StoreConfig(Type.S3, null, "b", null, null, null, "key", "s3cr3t", false);
    final var config =
        new ServiceConfig(new InetSocketAddress(0)).withClusters(Map.of("east", store));

    // when, then
    assertThat(config.toString()).contains("clusters=[east]").doesNotContain("s3cr3t");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "_groups"})
  void shouldRejectAClusterIdThatIsBlankOrStartsWithAnUnderscore(final String cluster) {
    // given
    final var config = new ServiceConfig(new InetSocketAddress(0));
    final var clusters = Map.of(cluster, StoreConfig.filesystem(Path.of("/data")));

    // when, then
    assertThatThrownBy(() -> config.withClusters(clusters))
        .isInstanceOf(InvalidServiceSettingException.class)
        .hasMessage(
            "Expected a cluster id to be set and not start with '_', but got '%s'"
                .formatted(cluster));
  }

  @Test
  void shouldRejectAPeerThatIsNoHttpUrl() {
    // given
    final var config = new ServiceConfig(new InetSocketAddress(0));

    // when, then
    assertThatThrownBy(() -> config.withPeers(List.of(URI.create("ftp://peer"))))
        .isInstanceOf(InvalidServiceSettingException.class)
        .hasMessage("Expected a peer to be an http or https URL with a host, but got 'ftp://peer'");
  }

  @Test
  void shouldRejectAKeepAliveOfZero() {
    // given
    final var config = new ServiceConfig(new InetSocketAddress(0));

    // when, then
    assertThatThrownBy(() -> config.withKeepAlive(Duration.ZERO))
        .isInstanceOf(InvalidServiceSettingException.class)
        .hasMessage("Expected the keep-alive to be longer than zero, but got PT0S");
  }

  @Test
  void shouldRejectCredentialsThatAreNoJsonObject() {
    // given
    final var config = new ServiceConfig(new InetSocketAddress(0));

    // when, then
    assertThatThrownBy(() -> config.withCredentialsJson("token"))
        .isInstanceOf(InvalidServiceSettingException.class)
        .hasMessage("Expected the credentials to be a JSON object, but got something else");
  }

  @Test
  void shouldStubTheCredentialsAsTheStoreSettingsOfAFilesystemStore() {
    // given
    final var store = StoreConfig.filesystem(Path.of("/data/event-plane"));

    // when
    final var json = StoreCredentials.json(store);

    // then
    assertThat(json)
        .isEqualTo(
            "{\"type\":\"filesystem\",\"path\":\"/data/event-plane\",\"forcePathStyle\":false}");
  }
}
