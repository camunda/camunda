/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Cluster-shared issuer-aware {@link JwtDecoder}. Holds one underlying {@link NimbusJwtDecoder} per
 * issuer URI (lazily built via {@link NimbusJwtDecoder#withIssuerLocation(String)}), and at decode
 * time peeks at the JWT's {@code iss} claim and dispatches to the matching underlying decoder.
 *
 * <p>This decoder is intentionally permissive — it validates that the token was signed by an issuer
 * the platform knows about. The per-chain "tenant A's API may only accept tenant A's tokens"
 * decision lives in {@code PerTenantSecurityChainFactory#buildApiChain} as an issuer allowlist
 * (spec D6: allowlist, not denylist).
 *
 * <p>JWT parsing for the {@code iss} preview uses the same {@code NimbusJwtDecoder} that will
 * validate it; we use the {@link NimbusJwtDecoder} for both peek and validate by first running an
 * unsigned-claims peek (which still parses the compact form), then dispatching to the matching
 * decoder.
 */
@NullMarked
public final class PtIssuerAwareJwtDecoder implements JwtDecoder {

  private final Map<String, JwtDecoder> decodersByIssuer;

  public PtIssuerAwareJwtDecoder(final Collection<String> issuerUris) {
    final Map<String, JwtDecoder> built = new LinkedHashMap<>();
    for (final String issuer : issuerUris) {
      built.put(issuer, NimbusJwtDecoder.withIssuerLocation(issuer).build());
    }
    decodersByIssuer = Map.copyOf(built);
  }

  /** Exposed for tests / diagnostics. */
  public Set<String> knownIssuers() {
    return decodersByIssuer.keySet();
  }

  @Override
  public Jwt decode(final String token) throws JwtException {
    final String issuer = peekIssuer(token);
    final JwtDecoder delegate = decodersByIssuer.get(issuer);
    if (delegate == null) {
      throw new JwtException(
          "Unknown JWT issuer '" + issuer + "'; known issuers: " + decodersByIssuer.keySet());
    }
    return delegate.decode(token);
  }

  /**
   * Parse the JWT payload (middle base64url segment) and read the {@code iss} claim without
   * verifying the signature. The chosen delegate will then perform full signature + issuer
   * validation on the original compact token.
   */
  private static String peekIssuer(final String token) {
    try {
      final var jwt = JWTParser.parse(token);
      final Object iss = jwt.getJWTClaimsSet().getClaim(JwtClaimNames.ISS);
      if (iss == null) {
        throw new JwtException("JWT is missing the 'iss' claim");
      }
      return iss.toString();
    } catch (final ParseException e) {
      throw new JwtException("Failed to parse JWT to read 'iss' claim", e);
    }
  }
}
