/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CertificateClientAssertionService {
  private static final Logger LOG =
      LoggerFactory.getLogger(CertificateClientAssertionService.class);
  private static final long JWT_EXPIRATION_SECONDS = 300; // 5 minutes

  public String createClientAssertion(
      final OidcAuthenticationConfiguration config, final String tokenEndpoint) {
    try {
      // Load private key and certificate from keystore
      final PrivateKey privateKey = CertificateKeyStoreLoader.loadPrivateKey(config);
      final Certificate certificate = CertificateKeyStoreLoader.loadCertificate(config);

      // Calculate certificate thumbprint (SHA-1 hash) for MS Entra
      final String thumbprint = calculateCertificateThumbprint(certificate);
      LOG.info("Using certificate thumbprint as Key ID: {}", thumbprint);

      // Create JWT claims
      final JWTClaimsSet.Builder claimsBuilder =
          new JWTClaimsSet.Builder()
              .subject(config.getClientId())
              .issuer(config.getClientId())
              .audience(tokenEndpoint)
              .jwtID(UUID.randomUUID().toString())
              .issueTime(Date.from(Instant.now()))
              .expirationTime(Date.from(Instant.now().plusSeconds(JWT_EXPIRATION_SECONDS)));

      // Create signed JWT with certificate thumbprint as Key ID
      final SignedJWT signedJWT =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256)
                  .keyID(thumbprint) // Use certificate thumbprint instead of alias
                  .build(),
              claimsBuilder.build());

      // Sign the JWT
      signedJWT.sign(new RSASSASigner((RSAPrivateKey) privateKey));

      return signedJWT.serialize();
    } catch (final Exception e) {
      LOG.error("Failed to create client assertion JWT", e);
      throw new RuntimeException("Failed to create client assertion", e);
    }
  }

  private String calculateCertificateThumbprint(final Certificate certificate) {
    try {
      final MessageDigest digest =
          MessageDigest.getInstance(ClientAssertionConstants.HASH_ALGORITHM_SHA1);
      final byte[] certBytes = certificate.getEncoded();
      final byte[] thumbprintBytes = digest.digest(certBytes);

      // Convert to Base64 URL-safe encoding (no padding) as expected by MS Entra
      return Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprintBytes);
    } catch (final Exception e) {
      LOG.error("Failed to calculate certificate thumbprint", e);
      throw new RuntimeException("Failed to calculate certificate thumbprint", e);
    }
  }
}
