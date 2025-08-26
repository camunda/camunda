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

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.junit.jupiter.api.io.TempDir;

public class CertificateKeyStoreLoaderTest {

  @TempDir private Path tempDir;

  private OidcAuthenticationConfiguration config;
  private String keystorePath;
  private final String keystorePassword = "test-password";
  private final String keyAlias = "test-key";
  private final String keyPassword = "key-password";

  @BeforeEach
  void setUp() throws Exception {
    // Create test keystore
    keystorePath = createTestKeystore();

    // Create configuration mock
    config = new OidcAuthenticationConfiguration();
    config.setClientAssertionKeystorePath(keystorePath);
    config.setClientAssertionKeystorePassword(keystorePassword);
    config.setClientAssertionKeystoreKeyAlias(keyAlias);
    config.setClientAssertionKeystoreKeyPassword(keyPassword);
  }

  @Test
  void shouldLoadPrivateKeyFromKeystore() throws Exception {
    // when
    final PrivateKey privateKey = CertificateKeyStoreLoader.loadPrivateKey(config);

    // then
    assertThat(privateKey).isNotNull();
    assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
    assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
  }

  @Test
  void shouldLoadCertificateFromKeystore() throws Exception {
    // when
    final Certificate certificate = CertificateKeyStoreLoader.loadCertificate(config);

    // then
    assertThat(certificate).isNotNull();
    assertThat(certificate).isInstanceOf(X509Certificate.class);

    final X509Certificate x509Cert = (X509Certificate) certificate;
    assertThat(x509Cert.getSubjectX500Principal().getName()).contains("CN=Test Certificate");
  }

  @Test
  void shouldThrowKeyStoreExceptionForInvalidKeystorePath() {
    // given
    config.setClientAssertionKeystorePath("/nonexistent/path");

    // when/then
    assertThatThrownBy(() -> CertificateKeyStoreLoader.loadPrivateKey(config))
        .isInstanceOf(IOException.class);
  }

  @Test
  void shouldThrowKeyStoreExceptionForWrongPassword() {
    // given
    config.setClientAssertionKeystorePassword("wrong-password");

    // when/then
    assertThatThrownBy(() -> CertificateKeyStoreLoader.loadPrivateKey(config))
        .isInstanceOf(IOException.class);
  }

  @Test
  void shouldThrowUnrecoverableKeyExceptionForWrongKeyPassword() {
    // given
    config.setClientAssertionKeystoreKeyPassword("wrong-key-password");

    // when/then
    assertThatThrownBy(() -> CertificateKeyStoreLoader.loadPrivateKey(config))
        .isInstanceOf(java.security.UnrecoverableKeyException.class);
  }

  @Test
  void shouldReturnNullForNonExistentAlias() throws Exception {
    // given
    config.setClientAssertionKeystoreKeyAlias("nonexistent-alias");

    // when
    final PrivateKey result = CertificateKeyStoreLoader.loadPrivateKey(config);

    // then - KeyStore.getKey() returns null for non-existent aliases
    assertThat(result).isNull();
  }

  private String createTestKeystore() throws Exception {
    // Generate key pair
    final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    final KeyPair keyPair = keyGen.generateKeyPair();

    // Create self-signed certificate
    final X509Certificate certificate = createSelfSignedCertificate(keyPair);

    // Create keystore
    final KeyStore keystore = KeyStore.getInstance("PKCS12");
    keystore.load(null, null);

    // Add key and certificate to keystore
    keystore.setKeyEntry(
        keyAlias, keyPair.getPrivate(), keyPassword.toCharArray(), new Certificate[] {certificate});

    // Save keystore to file
    final File keystoreFile = tempDir.resolve("test.p12").toFile();
    try (final FileOutputStream fos = new FileOutputStream(keystoreFile)) {
      keystore.store(fos, keystorePassword.toCharArray());
    }

    return keystoreFile.getAbsolutePath();
  }

  private X509Certificate createSelfSignedCertificate(final KeyPair keyPair) throws Exception {
    // Add BouncyCastle provider if not already present
    if (java.security.Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      java.security.Security.addProvider(new BouncyCastleProvider());
    }

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
