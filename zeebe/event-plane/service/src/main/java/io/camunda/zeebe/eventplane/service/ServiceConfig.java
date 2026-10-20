/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * How an {@link EventPlaneService} runs.
 *
 * @param address where to listen, with port 0 for any free port
 * @param peers the base URLs of the other instances of the service, such as {@code
 *     http://service-1:8085}, which get each notice that doesn't come from a peer
 * @param keepAlive how long a notification stream may be idle before it gets a keep-alive comment
 * @param credentialsJson the JSON object that {@code GET /v1/credentials} answers: a stub of what a
 *     worker needs to open the store
 */
public record ServiceConfig(
    InetSocketAddress address, List<URI> peers, Duration keepAlive, String credentialsJson) {

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
  }

  /** A service on the address, without peers, with the default keep-alive and credentials. */
  public ServiceConfig(final InetSocketAddress address) {
    this(address, List.of(), DEFAULT_KEEP_ALIVE, DEFAULT_CREDENTIALS_JSON);
  }

  public ServiceConfig withPeers(final List<URI> newPeers) {
    return new ServiceConfig(address, newPeers, keepAlive, credentialsJson);
  }

  public ServiceConfig withKeepAlive(final Duration newKeepAlive) {
    return new ServiceConfig(address, peers, newKeepAlive, credentialsJson);
  }

  public ServiceConfig withCredentialsJson(final String newCredentialsJson) {
    return new ServiceConfig(address, peers, keepAlive, newCredentialsJson);
  }

  /** Like the record's, but without the credentials, which can hold a secret. */
  @Override
  public String toString() {
    return "ServiceConfig[address=%s, peers=%s, keepAlive=%s, credentialsJson=***]"
        .formatted(address, peers, keepAlive);
  }
}
