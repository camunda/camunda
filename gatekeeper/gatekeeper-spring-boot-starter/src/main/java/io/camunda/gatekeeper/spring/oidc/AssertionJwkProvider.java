/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.oidc;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import io.camunda.gatekeeper.config.AssertionConfig;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.interfaces.RSAPublicKey;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates JWK keys for private_key_jwt client authentication from a PKCS12 keystore configured in
 * the OIDC assertion settings.
 */
public final class AssertionJwkProvider {

  private static final Logger LOG = LoggerFactory.getLogger(AssertionJwkProvider.class);

  private final OidcAuthenticationConfigurationRepository oidcConfigRepository;

  public AssertionJwkProvider(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    this.oidcConfigRepository = oidcConfigRepository;
  }

  /**
   * Creates a {@link JWK} for the given client registration ID using the assertion configuration
   * from the OIDC provider settings.
   *
   * @param clientRegistrationId the client registration ID
   * @return a JWK for use in private_key_jwt client authentication
   * @throws IllegalArgumentException if no OIDC configuration is found for the registration ID
   * @throws IllegalStateException if assertion/keystore configuration is missing or invalid
   */
  public JWK createJwk(final String clientRegistrationId) {
    final var oidcConfig = oidcConfigRepository.getOidcConfigById(clientRegistrationId);
    if (oidcConfig == null) {
      throw new IllegalArgumentException(
          "No OIDC configuration found for registration ID: " + clientRegistrationId);
    }
    final var assertionConfig = oidcConfig.assertion();
    if (assertionConfig == null || !assertionConfig.isConfigured()) {
      throw new IllegalStateException(
          "No assertion/keystore configuration found for registration ID: " + clientRegistrationId);
    }
    assertionConfig.validate();
    final var alias = assertionConfig.keyAlias();
    final var password = assertionConfig.keyPassword().toCharArray();
    try {
      final KeyStore keyStore = loadKeystore(assertionConfig);
      final var pk = (PrivateKey) keyStore.getKey(alias, password);
      final var cert = keyStore.getCertificate(alias);
      final var pub = (RSAPublicKey) cert.getPublicKey();
      return new RSAKey.Builder(pub)
          .privateKey(pk)
          .x509CertChain(List.of(Base64.encode(cert.getEncoded())))
          .keyID(generateKid(cert, assertionConfig))
          .x509CertSHA256Thumbprint(thumbprintSha256(cert))
          .build();
    } catch (final Exception e) {
      throw new IllegalStateException(
          "Unable to load keystore for client: " + clientRegistrationId, e);
    }
  }

  private static KeyStore loadKeystore(final AssertionConfig config) throws Exception {
    final var keyStore = KeyStore.getInstance("PKCS12");
    try (final var fis = new FileInputStream(config.keystorePath())) {
      keyStore.load(fis, config.keystorePassword().toCharArray());
    }
    return keyStore;
  }

  private static Base64URL thumbprintSha256(final Certificate cert)
      throws NoSuchAlgorithmException, CertificateEncodingException {
    final var sha256 = MessageDigest.getInstance("SHA-256");
    final byte[] digest = sha256.digest(cert.getEncoded());
    return Base64URL.encode(digest);
  }

  private static String generateKid(final Certificate cert, final AssertionConfig config) {
    final var digestAlg = getKidDigestAlgorithmInstance(config.kidDigestAlgorithm());
    final var sourceBytes = getKidSourceBytes(cert, config.kidSource());
    final var digest = digestAlg.digest(sourceBytes);
    return switch (config.kidEncoding()) {
      case BASE64URL -> Base64URL.encode(digest).toString();
      case HEX -> getHexFormatWithCase(config.kidCase()).formatHex(digest);
    };
  }

  private static MessageDigest getKidDigestAlgorithmInstance(
      final AssertionConfig.KidDigestAlgorithm alg) {
    try {
      return switch (alg) {
        case SHA256 -> MessageDigest.getInstance("SHA-256");
        case SHA1 -> MessageDigest.getInstance("SHA-1");
      };
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("Failed to instantiate digest algorithm", e);
    }
  }

  private static byte[] getKidSourceBytes(
      final Certificate cert, final AssertionConfig.KidSource source) {
    try {
      return switch (source) {
        case PUBLIC_KEY -> cert.getPublicKey().getEncoded();
        case CERTIFICATE -> cert.getEncoded();
      };
    } catch (final CertificateEncodingException e) {
      throw new IllegalStateException("Failed to fetch encoded kid source", e);
    }
  }

  private static HexFormat getHexFormatWithCase(final AssertionConfig.KidCase kidCase) {
    return switch (kidCase) {
      case UPPER -> HexFormat.of().withUpperCase();
      case LOWER -> HexFormat.of().withLowerCase();
      case null -> HexFormat.of();
    };
  }
}
