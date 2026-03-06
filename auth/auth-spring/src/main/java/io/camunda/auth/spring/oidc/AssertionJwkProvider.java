/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import io.camunda.auth.spring.config.AssertionConfiguration;
import io.camunda.auth.spring.config.AssertionConfiguration.KidCase;
import io.camunda.auth.spring.config.AssertionConfiguration.KidDigestAlgorithm;
import io.camunda.auth.spring.config.AssertionConfiguration.KidSource;
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

public class AssertionJwkProvider {

  private static final Logger LOG = LoggerFactory.getLogger(AssertionJwkProvider.class);
  private final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository;

  public AssertionJwkProvider(
      final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
    this.oidcAuthenticationConfigurationRepository = oidcAuthenticationConfigurationRepository;
  }

  public JWK createJwk(final String clientRegistrationId) {
    final var assertionConfig =
        oidcAuthenticationConfigurationRepository
            .getOidcAuthenticationConfigurationById(clientRegistrationId)
            .getAssertion();
    final var keystoreConfig = assertionConfig.getKeystore();
    final var alias = keystoreConfig.getKeyAlias();
    final var password = keystoreConfig.getKeyPassword().toCharArray();
    try {
      final KeyStore keyStore = keystoreConfig.loadKeystore();
      final var pk = (PrivateKey) keyStore.getKey(alias, password);
      final var cert = keyStore.getCertificate(alias);
      final var pub = (RSAPublicKey) cert.getPublicKey();
      return new RSAKey.Builder(pub)
          .privateKey(pk)
          .x509CertChain(List.of(Base64.encode(cert.getEncoded())))
          .keyID(generateKid(cert, assertionConfig))
          .x509CertSHA256Thumbprint(thumbprintCertificateSha256(cert))
          .build();
    } catch (final Exception e) {
      throw new IllegalStateException("Unable to load keystore", e);
    }
  }

  private Base64URL thumbprintCertificateSha256(final Certificate cert)
      throws NoSuchAlgorithmException, CertificateEncodingException {
    final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    final byte[] digest = sha256.digest(cert.getEncoded());
    return Base64URL.encode(digest);
  }

  private String generateKid(final Certificate cert, final AssertionConfiguration config) {
    final var digestAlg = getKidDigestAlgorithmInstance(config.getKidDigestAlgorithm());
    final var sourceBytes = getKidSourceBytes(cert, config.getKidSource());
    final var digest = digestAlg.digest(sourceBytes);
    final var kid =
        switch (config.getKidEncoding()) {
          case BASE64URL -> Base64URL.encode(digest).toString();
          case HEX -> getHexFormatWithCase(config.getKidCase()).formatHex(digest);
        };
    LOG.debug("generated kid '{}' from keystore '{}'", kid, config.getKeystore().getPath());
    return kid;
  }

  private MessageDigest getKidDigestAlgorithmInstance(final KidDigestAlgorithm alg) {
    try {
      return switch (alg) {
        case SHA256 -> MessageDigest.getInstance("SHA-256");
        case SHA1 -> MessageDigest.getInstance("SHA-1");
      };
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("failed to instantiate digest algorithm", e);
    }
  }

  private byte[] getKidSourceBytes(final Certificate cert, final KidSource source) {
    try {
      return switch (source) {
        case PUBLIC_KEY -> cert.getPublicKey().getEncoded();
        case CERTIFICATE -> cert.getEncoded();
      };
    } catch (final CertificateEncodingException e) {
      throw new IllegalStateException("failed to fetch encoded kid source", e);
    }
  }

  private HexFormat getHexFormatWithCase(final KidCase kidCase) {
    return switch (kidCase) {
      case UPPER -> HexFormat.of().withUpperCase();
      case LOWER -> HexFormat.of().withLowerCase();
      case null -> HexFormat.of();
    };
  }
}
