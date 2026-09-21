/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * An in-JVM OIDC discovery/JWKS server for signing test bearer tokens, mirroring the pattern from
 * {@code PhysicalTenantApiChainIsolationIT}. Shared by {@link CslChainIntegrationTest} and {@link
 * OptimizeBearerPermissionFilterIntegrationTest}.
 */
final class JwksTestServer {

  private final HttpServer httpServer;
  private final String kid;
  private final JWSSigner signer;

  private JwksTestServer(final HttpServer httpServer, final String kid, final JWSSigner signer) {
    this.httpServer = httpServer;
    this.kid = kid;
    this.signer = signer;
  }

  static JwksTestServer start(final String kid) throws Exception {
    final var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    final var pair = generator.generateKeyPair();
    final var jwk =
        new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .privateKey((RSAPrivateKey) pair.getPrivate())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(kid)
            .build();
    final var jwkSetJson = new JWKSet(jwk).toPublicJWKSet().toString();
    final var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    final var base = "http://127.0.0.1:" + httpServer.getAddress().getPort();
    final var discoveryDoc =
        """
        {
          "issuer": "%s",
          "authorization_endpoint": "%s/auth",
          "token_endpoint": "%s/token",
          "jwks_uri": "%s/jwks",
          "response_types_supported": ["code"],
          "subject_types_supported": ["public"],
          "id_token_signing_alg_values_supported": ["RS256"]
        }
        """
            .formatted(base, base, base, base);

    httpServer.createContext(
        "/jwks",
        exchange -> {
          final var body = jwkSetJson.getBytes(UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (exchange) {
            exchange.getResponseBody().write(body);
          }
        });
    httpServer.createContext(
        "/.well-known/openid-configuration",
        exchange -> {
          final var body = discoveryDoc.getBytes(UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (exchange) {
            exchange.getResponseBody().write(body);
          }
        });
    httpServer.createContext(
        "/unreachable/.well-known/openid-configuration",
        exchange -> {
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    httpServer.start();
    return new JwksTestServer(httpServer, kid, new RSASSASigner(jwk));
  }

  String kid() {
    return kid;
  }

  JWSSigner signer() {
    return signer;
  }

  /**
   * An issuer this server declines to describe: its discovery endpoint always answers 500. Stands
   * in for a provider that is down, without depending on a port nothing listens on.
   */
  String unreachableIssuerUri() {
    return issuerUri() + "/unreachable";
  }

  String issuerUri() {
    return "http://127.0.0.1:" + httpServer.getAddress().getPort();
  }

  void stop() {
    httpServer.stop(0);
  }
}
