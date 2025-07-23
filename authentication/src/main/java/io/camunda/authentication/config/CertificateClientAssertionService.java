/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for creating JWT client assertions using X.509 certificates for Microsoft Entra ID authentication.
 * Implements the certificate credential flow as described in:
 * https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow#request-an-access-token-with-a-certificate-credential
 */
@Service
public class CertificateClientAssertionService {

  private static final Logger LOG = LoggerFactory.getLogger(CertificateClientAssertionService.class);
  private static final String JWT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  private static final int ASSERTION_VALIDITY_MINUTES = 10; // Microsoft recommends max 10 minutes

  /**
   * Creates a JWT client assertion using the configured certificate.
   *
   * @param configuration The OIDC configuration containing certificate details
   * @param tokenUri The token endpoint URI to use as the audience
   * @return The JWT client assertion string
   * @throws RuntimeException if certificate loading or JWT creation fails
   */
  public String createClientAssertion(final OidcAuthenticationConfiguration configuration, final String tokenUri) {
    if (!configuration.isClientAssertionEnabled()) {
      throw new IllegalStateException("Certificate client assertion is not enabled");
    }

    try {
      final CertificateData certData = loadCertificate(configuration);
      return buildJwtAssertion(configuration.getClientId(), tokenUri, certData);
    } catch (final Exception e) {
      LOG.error("Failed to create client assertion", e);
      throw new RuntimeException("Failed to create client assertion", e);
    }
  }

  private CertificateData loadCertificate(final OidcAuthenticationConfiguration configuration) 
      throws IOException, GeneralSecurityException {
    
    try (final FileInputStream stream = new FileInputStream(configuration.getClientAssertionKeystorePath())) {
      final KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(stream, configuration.getClientAssertionKeystorePassword().toCharArray());

      final String keyAlias = configuration.getClientAssertionKeystoreKeyAlias() != null 
          ? configuration.getClientAssertionKeystoreKeyAlias()
          : keyStore.aliases().nextElement(); // Use first available alias if none specified

      final String keyPassword = configuration.getClientAssertionKeystoreKeyPassword() != null
          ? configuration.getClientAssertionKeystoreKeyPassword()
          : configuration.getClientAssertionKeystorePassword(); // Use keystore password if key password not specified

      final RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray());
      final X509Certificate certificate = (X509Certificate) keyStore.getCertificate(keyAlias);
      final RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();

      if (privateKey == null || certificate == null) {
        throw new IllegalStateException("Unable to load private key or certificate from keystore");
      }

      return new CertificateData(privateKey, publicKey, certificate);
    }
  }

  private String buildJwtAssertion(final String clientId, final String tokenUri, final CertificateData certData) {
    final Date now = new Date();
    final Date expiry = new Date(now.getTime() + (ASSERTION_VALIDITY_MINUTES * 60 * 1000L));
    
    // Generate X.509 certificate thumbprint (x5t) as required by Microsoft Entra ID
    final String x5t = generateX5tThumbprint(certData.certificate);

    final Map<String, Object> header = new HashMap<>();
    header.put("alg", "RS256");
    header.put("typ", "JWT");
    header.put("x5t", x5t);

    final Algorithm algorithm = Algorithm.RSA256(certData.publicKey, certData.privateKey);

    return JWT.create()
        .withHeader(header)
        .withIssuer(clientId)
        .withSubject(clientId)
        .withAudience(tokenUri)
        .withIssuedAt(now)
        .withNotBefore(now)
        .withExpiresAt(expiry)
        .withJWTId(UUID.randomUUID().toString())
        .sign(algorithm);
  }

  /**
   * Generates the X.509 certificate thumbprint (x5t) using SHA-1 hash
   * as required by Microsoft Entra ID for certificate authentication.
   */
  private static String generateX5tThumbprint(final X509Certificate certificate) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-1");
      final byte[] encoded = digest.digest(certificate.getEncoded());
      return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to generate x5t thumbprint", e);
    }
  }

  private static class CertificateData {
    final RSAPrivateKey privateKey;
    final RSAPublicKey publicKey;
    final X509Certificate certificate;

    CertificateData(final RSAPrivateKey privateKey, final RSAPublicKey publicKey, final X509Certificate certificate) {
      this.privateKey = privateKey;
      this.publicKey = publicKey;
      this.certificate = certificate;
    }
  }
}
