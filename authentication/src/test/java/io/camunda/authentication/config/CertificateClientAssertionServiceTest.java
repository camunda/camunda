/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class CertificateClientAssertionServiceTest {

  @Mock private OidcAuthenticationConfiguration config;
  @Mock private Certificate certificate;

  private CertificateClientAssertionService service;
  private KeyPair keyPair;
  private X509Certificate testCertificate;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    service = new CertificateClientAssertionService();

    // Generate test key pair
    final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    keyPair = keyGen.generateKeyPair();

    // Create a test certificate
    testCertificate = createTestCertificate();
  }

  @Test
  void shouldCreateClientAssertionJwt() throws Exception {
    // given
    when(config.getClientId()).thenReturn("test-client-id");
    final String tokenEndpoint = "https://login.microsoftonline.com/tenant/oauth2/v2.0/token";

    try (final MockedStatic<CertificateKeyStoreLoader> loaderMock =
        mockStatic(CertificateKeyStoreLoader.class)) {
      loaderMock
          .when(() -> CertificateKeyStoreLoader.loadPrivateKey(config))
          .thenReturn(keyPair.getPrivate());
      loaderMock
          .when(() -> CertificateKeyStoreLoader.loadCertificate(config))
          .thenReturn(testCertificate);

      // when
      final String clientAssertion = service.createClientAssertion(config, tokenEndpoint);

      // then
      assertThat(clientAssertion).isNotNull().isNotEmpty();

      // Verify JWT structure and claims
      final SignedJWT signedJWT = SignedJWT.parse(clientAssertion);
      final JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

      assertThat(claims.getSubject()).isEqualTo("test-client-id");
      assertThat(claims.getIssuer()).isEqualTo("test-client-id");
      assertThat(claims.getAudience()).containsExactly(tokenEndpoint);
      assertThat(claims.getJWTID()).isNotNull();
      assertThat(claims.getIssueTime()).isNotNull();
      assertThat(claims.getExpirationTime()).isNotNull();

      // Verify expiration time is approximately 5 minutes from now
      final Instant expiration = claims.getExpirationTime().toInstant();
      final Instant now = Instant.now();
      final long diffMinutes = ChronoUnit.MINUTES.between(now, expiration);
      assertThat(diffMinutes).isBetween(4L, 5L);

      // Verify JWT header contains certificate thumbprint as Key ID
      final String keyId = signedJWT.getHeader().getKeyID();
      assertThat(keyId).isNotNull().isNotEmpty();

      // Verify signature
      final JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
      assertThat(signedJWT.verify(verifier)).isTrue();
    }
  }

  @Test
  void shouldCalculateCertificateThumbprint() throws Exception {
    // given
    when(config.getClientId()).thenReturn("test-client");
    final String tokenEndpoint = "https://example.com/token";

    try (final MockedStatic<CertificateKeyStoreLoader> loaderMock =
        mockStatic(CertificateKeyStoreLoader.class)) {
      loaderMock
          .when(() -> CertificateKeyStoreLoader.loadPrivateKey(config))
          .thenReturn(keyPair.getPrivate());
      loaderMock
          .when(() -> CertificateKeyStoreLoader.loadCertificate(config))
          .thenReturn(testCertificate);

      // when
      final String clientAssertion = service.createClientAssertion(config, tokenEndpoint);
      final SignedJWT signedJWT = SignedJWT.parse(clientAssertion);
      final String keyId = signedJWT.getHeader().getKeyID();

      // then - verify thumbprint calculation
      final MessageDigest digest =
          MessageDigest.getInstance(ClientAssertionConstants.HASH_ALGORITHM_SHA1);
      final byte[] certBytes = testCertificate.getEncoded();
      final byte[] thumbprintBytes = digest.digest(certBytes);
      final String expectedThumbprint =
          Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprintBytes);

      assertThat(keyId).isEqualTo(expectedThumbprint);
    }
  }

  @Test
  void shouldThrowRuntimeExceptionOnKeyLoadFailure() {
    // given
    when(config.getClientId()).thenReturn("test-client");
    final String tokenEndpoint = "https://example.com/token";

    try (final MockedStatic<CertificateKeyStoreLoader> loaderMock =
        mockStatic(CertificateKeyStoreLoader.class)) {
      loaderMock
          .when(() -> CertificateKeyStoreLoader.loadPrivateKey(config))
          .thenThrow(new RuntimeException("Keystore error"));

      // when/then
      assertThatThrownBy(() -> service.createClientAssertion(config, tokenEndpoint))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to create client assertion");
    }
  }

  @Test
  void shouldThrowRuntimeExceptionOnCertificateLoadFailure() {
    // given
    when(config.getClientId()).thenReturn("test-client");
    final String tokenEndpoint = "https://example.com/token";

    try (final MockedStatic<CertificateKeyStoreLoader> loaderMock =
        mockStatic(CertificateKeyStoreLoader.class)) {
      loaderMock
          .when(() -> CertificateKeyStoreLoader.loadPrivateKey(config))
          .thenReturn(keyPair.getPrivate());
      loaderMock
          .when(() -> CertificateKeyStoreLoader.loadCertificate(config))
          .thenThrow(new RuntimeException("Certificate error"));

      // when/then
      assertThatThrownBy(() -> service.createClientAssertion(config, tokenEndpoint))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to create client assertion");
    }
  }

  private X509Certificate createTestCertificate() throws Exception {
    // Use BouncyCastle to generate a proper test certificate programmatically
    // Add BouncyCastle provider if not already present
    if (java.security.Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      java.security.Security.addProvider(new BouncyCastleProvider());
    }

    // Generate key pair
    final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    final KeyPair keyPair = keyGen.generateKeyPair();

    final Instant now = Instant.now();
    final Date notBefore = Date.from(now);
    final Date notAfter = Date.from(now.plus(365, ChronoUnit.DAYS));

    final X500Principal subject = new X500Principal("CN=Test Certificate, O=Test Org, C=US");
    final java.math.BigInteger serial = java.math.BigInteger.valueOf(1);

    final X509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.getPublic());

    final ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());

    final X509CertificateHolder certHolder = certBuilder.build(signer);
    return new JcaX509CertificateConverter().getCertificate(certHolder);
  }
}
