/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.hashicorpvault;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal stand-in for the HashiCorp Vault HTTP API: AppRole sign-in, token renew/revoke, and KV
 * v2 reads. Lets the unit tests drive the failure modes a real server only shows under load or over
 * time (expiry, revocation, sealing) without waiting for them.
 */
final class FakeHashiCorpVault implements AutoCloseable {

  static final String ROLE_ID = "camunda-role";

  final Map<String, String> entries = new ConcurrentHashMap<>();
  final Set<String> deniedPaths = ConcurrentHashMap.newKeySet();
  final AtomicInteger logins = new AtomicInteger();
  final AtomicInteger renewals = new AtomicInteger();
  final AtomicInteger revocations = new AtomicInteger();
  final AtomicInteger reads = new AtomicInteger();

  volatile String acceptedSecretId = "secret-id-1";
  volatile long loginLeaseSeconds = 3600;
  volatile long renewLeaseSeconds = 3600;
  volatile boolean renewable = true;
  volatile int forcedStatus;
  volatile String validToken = "";

  private final HttpServer server;

  FakeHashiCorpVault() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  URI address() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  /** Invalidates the current token, as {@code vault token revoke} or an expiry would. */
  void revokeCurrentToken() {
    validToken = "";
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(final HttpExchange exchange) throws IOException {
    try (exchange) {
      final var path = exchange.getRequestURI().getPath();
      final var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      if (forcedStatus != 0) {
        respond(exchange, forcedStatus, "{\"errors\":[\"Vault is sealed\"]}");
        return;
      }
      if (path.equals("/v1/auth/approle/login")) {
        if (!body.contains("\"role_id\":\"" + ROLE_ID + "\"")
            || !body.contains("\"secret_id\":\"" + acceptedSecretId + "\"")) {
          respond(exchange, 400, "{\"errors\":[\"invalid role or secret ID\"]}");
          return;
        }
        validToken = "token-" + logins.incrementAndGet();
        respond(exchange, 200, auth(validToken, loginLeaseSeconds));
        return;
      }
      final var token = exchange.getRequestHeaders().getFirst("X-Vault-Token");
      if (token == null || !token.equals(validToken)) {
        respond(exchange, 403, "{\"errors\":[\"permission denied\"]}");
        return;
      }
      switch (path) {
        case "/v1/auth/token/renew-self" -> {
          renewals.incrementAndGet();
          respond(exchange, 200, auth(token, renewLeaseSeconds));
        }
        case "/v1/auth/token/revoke-self" -> {
          revocations.incrementAndGet();
          validToken = "";
          respond(exchange, 204, "");
        }
        default -> {
          if (!path.startsWith("/v1/secret/data/")) {
            respond(exchange, 404, "{\"errors\":[]}");
            return;
          }
          reads.incrementAndGet();
          final var entryPath = path.substring("/v1/secret/data/".length());
          if (deniedPaths.contains(entryPath)) {
            respond(exchange, 403, "{\"errors\":[\"permission denied\"]}");
            return;
          }
          final var data = entries.get(entryPath);
          if (data == null) {
            respond(exchange, 404, "{\"errors\":[]}");
            return;
          }
          respond(exchange, 200, "{\"data\":{\"data\":" + data + ",\"metadata\":{\"version\":1}}}");
        }
      }
    }
  }

  private String auth(final String token, final long lease) {
    return "{\"renewable\":false,\"auth\":{\"client_token\":\""
        + token
        + "\",\"lease_duration\":"
        + lease
        + ",\"renewable\":"
        + renewable
        + ",\"policies\":[\"camunda\"]}}";
  }

  private static void respond(final HttpExchange exchange, final int status, final String body)
      throws IOException {
    final var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    if (bytes.length == 0) {
      exchange.sendResponseHeaders(status, -1);
      return;
    }
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }
}
