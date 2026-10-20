/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.store.StoreConfig;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * How an {@link EventPlaneService} runs.
 *
 * @param address where to listen, with port 0 for any free port
 * @param peers the base URLs of the other instances of the service, such as {@code
 *     http://service-1:8085}, which get each notice that doesn't come from a peer
 * @param keepAlive how long a notification stream may be idle before it gets a keep-alive comment
 * @param credentialsJson the JSON object that {@code GET /v1/credentials} answers: a stub of what a
 *     worker needs to open the store
 * @param clusters the store that each cluster, by id, writes to: {@code GET /v1/clusters} lists
 *     them, and {@code GET /v1/credentials?cluster=…} answers one with its secret
 */
public record ServiceConfig(
    InetSocketAddress address,
    List<URI> peers,
    Duration keepAlive,
    String credentialsJson,
    Map<String, StoreConfig> clusters) {

  /** How long a notification stream is idle before a keep-alive comment. */
  public static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(15);

  /** The answer of {@code GET /v1/credentials} if nothing is configured. */
  public static final String DEFAULT_CREDENTIALS_JSON = "{}";

  public ServiceConfig {
    peers = List.copyOf(peers);
    for (final var peer : peers) {
      final var scheme = peer.getScheme() == null ? "" : peer.getScheme().toLowerCase(Locale.ROOT);
      if (!(scheme.equals("http") || scheme.equals("https")) || peer.getHost() == null) {
        throw new InvalidServiceSettingException(
            "a peer", "to be an http or https URL with a host", "'" + peer + "'");
      }
    }
    if (keepAlive.isZero() || keepAlive.isNegative()) {
      throw new InvalidServiceSettingException(
          "the keep-alive", "to be longer than zero", keepAlive.toString());
    }
    if (!Json.isObject(credentialsJson)) {
      throw new InvalidServiceSettingException(
          "the credentials", "to be a JSON object", "something else");
    }
    clusters = Collections.unmodifiableMap(new TreeMap<>(clusters));
    for (final var cluster : clusters.keySet()) {
      if (cluster.isBlank() || cluster.startsWith("_")) {
        throw new InvalidServiceSettingException(
            "a cluster id", "to be set and not start with '_'", "'" + cluster + "'");
      }
    }
  }

  /**
   * A service on the address, without peers or clusters, with the default keep-alive and
   * credentials.
   */
  public ServiceConfig(final InetSocketAddress address) {
    this(address, List.of(), DEFAULT_KEEP_ALIVE, DEFAULT_CREDENTIALS_JSON, Map.of());
  }

  public ServiceConfig withPeers(final List<URI> newPeers) {
    return new ServiceConfig(address, newPeers, keepAlive, credentialsJson, clusters);
  }

  public ServiceConfig withKeepAlive(final Duration newKeepAlive) {
    return new ServiceConfig(address, peers, newKeepAlive, credentialsJson, clusters);
  }

  public ServiceConfig withCredentialsJson(final String newCredentialsJson) {
    return new ServiceConfig(address, peers, keepAlive, newCredentialsJson, clusters);
  }

  public ServiceConfig withClusters(final Map<String, StoreConfig> newClusters) {
    return new ServiceConfig(address, peers, keepAlive, credentialsJson, newClusters);
  }

  /** Like the record's, but without the credentials and the stores, which can hold secrets. */
  @Override
  public String toString() {
    return "ServiceConfig[address=%s, peers=%s, keepAlive=%s, credentialsJson=***, clusters=%s]"
        .formatted(address, peers, keepAlive, clusters.keySet());
  }
}
