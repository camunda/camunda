/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Minimal JWKS + OIDC discovery HTTP server backed by a freshly generated RSA key pair. Serves:
 *
 * <ul>
 *   <li>{@code /jwks} — the public JWK set (for token verification)
 *   <li>{@code /.well-known/openid-configuration} — a discovery document pointing back to this
 *       server
 * </ul>
 *
 * <p>Mirrors the {@code JwksTestServer} pattern from CSL's {@code ScopedJwtDecoderFactoryTest}.
 * Shared by {@link PhysicalTenantApiChainIsolationIT} and {@link PhysicalTenantDefaultAliasChainIT}
 * — package-private rather than nested in either, since neither owns it.
 */
final class JwksTestServer {

  private final HttpServer server;
  private final String kid;
  private final JWSSigner signer;

  private JwksTestServer(final HttpServer server, final String kid, final JWSSigner signer) {
    this.server = server;
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
          final var body = jwkSetJson.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (exchange) {
            exchange.getResponseBody().write(body);
          }
        });
    httpServer.createContext(
        "/.well-known/openid-configuration",
        exchange -> {
          final var body = discoveryDoc.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (exchange) {
            exchange.getResponseBody().write(body);
          }
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

  String issuerUri() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  void stop() {
    server.stop(0);
  }

  /** Signs a JWT for {@code issuer}, with {@code audiences} included only when non-empty. */
  static String signForIssuer(
      final JwksTestServer server, final String issuer, final List<String> audiences)
      throws Exception {
    final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(server.kid()).build();
    final var builder =
        new JWTClaimsSet.Builder()
            .subject("alice")
            .issuer(issuer)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(60)));
    if (!audiences.isEmpty()) {
      builder.audience(audiences);
    }
    final var jwt = new SignedJWT(header, builder.build());
    jwt.sign(server.signer());
    return jwt.serialize();
  }
}
