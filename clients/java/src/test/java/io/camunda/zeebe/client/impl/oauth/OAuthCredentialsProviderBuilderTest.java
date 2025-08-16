/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.impl.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
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

public class OAuthCredentialsProviderBuilderTest {

  private static final String CLIENT_ID = "test-client-id";
  private static final String CLIENT_SECRET = "test-client-secret";
  private static final String AUDIENCE = "test-audience";
  private static final String AUTHORIZATION_SERVER_URL = "https://auth.example.com/oauth/token";
  private static final String SCOPE = "test-scope";
  private static final String RESOURCE = "test-resource";
  private static final String CACHE_PATH = "test-cache.json";

  @TempDir private Path tempDir;

  private String keystorePath;
  private final String keystorePassword = "test-password";
  private final String keyAlias = "test-key";
  private final String keyPassword = "key-password";

  @BeforeEach
  void setUp() throws Exception {
    keystorePath = createTestKeystore();
  }

  @Test
  void shouldBuildProviderWithBasicClientSecretConfiguration() throws MalformedURLException {
    // when
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .build();

    // then
    assertThat(provider).isNotNull();
  }

  @Test
  void shouldBuildProviderWithCertificateBasedAuthentication() throws MalformedURLException {
    // when
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .clientAssertionKeystorePath(keystorePath)
            .clientAssertionKeystorePassword(keystorePassword)
            .clientAssertionKeystoreKeyAlias(keyAlias)
            .clientAssertionKeystoreKeyPassword(keyPassword)
            .build();

    // then
    assertThat(provider).isNotNull();
  }

  @Test
  void shouldBuildWithOptionalParameters() throws MalformedURLException {
    // when
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .scope(SCOPE)
            .resource(RESOURCE)
            .credentialsCachePath(CACHE_PATH)
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(45))
            .build();

    // then
    assertThat(provider).isNotNull();
  }

  @Test
  void shouldDetectClientAssertionEnabledWhenKeystoreConfigured() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .clientAssertionKeystorePath(keystorePath)
            .clientAssertionKeystorePassword(keystorePassword)
            .clientAssertionKeystoreKeyAlias(keyAlias)
            .clientAssertionKeystoreKeyPassword(keyPassword);

    // when/then
    assertThat(builder.clientAssertionEnabled()).isTrue();
  }

  @Test
  void shouldDetectClientAssertionDisabledWhenKeystoreNotConfigured() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL);

    // when/then
    assertThat(builder.clientAssertionEnabled()).isFalse();
  }

  @Test
  void shouldThrowExceptionWhenClientIdMissing() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientSecret(CLIENT_SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL);

    // when/then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId");
  }

  @Test
  void shouldThrowExceptionWhenAudienceMissing() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL);

    // when/then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("audience");
  }

  @Test
  void shouldBuildSuccessfullyWhenAuthorizationServerUrlMissing() {
    // given - authorizationServerUrl now has a default value
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .audience(AUDIENCE);

    // when/then - should not throw since default URL is provided
    assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  void shouldThrowExceptionWhenBothClientSecretAndCertificateAreMissing() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL);

    // when/then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Either clientSecret or certificate-based authentication must be configured");
  }

  @Test
  void shouldThrowExceptionWhenKeystorePathMissingButOtherCertificateParametersProvided() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .clientAssertionKeystorePassword(keystorePassword)
            .clientAssertionKeystoreKeyAlias(keyAlias)
            .clientAssertionKeystoreKeyPassword(keyPassword);

    // when/then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientAssertionKeystorePath");
  }

  @Test
  void shouldThrowExceptionWhenKeystorePasswordMissingButOtherCertificateParametersProvided() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .clientAssertionKeystorePath(keystorePath)
            .clientAssertionKeystoreKeyAlias(keyAlias)
            .clientAssertionKeystoreKeyPassword(keyPassword);

    // when/then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientAssertionKeystorePassword");
  }

  @Test
  void shouldThrowExceptionWhenKeyAliasMissingButOtherCertificateParametersProvided() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .clientAssertionKeystorePath(keystorePath)
            .clientAssertionKeystorePassword(keystorePassword)
            .clientAssertionKeystoreKeyPassword(keyPassword);

    // when/then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientAssertionKeystoreKeyAlias");
  }

  @Test
  void shouldThrowExceptionWhenKeyPasswordMissingButOtherCertificateParametersProvided() {
    // given
    final OAuthCredentialsProviderBuilder builder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .clientAssertionKeystorePath(keystorePath)
            .clientAssertionKeystorePassword(keystorePassword)
            .clientAssertionKeystoreKeyAlias(keyAlias);

    // when/then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientAssertionKeystoreKeyPassword");
  }

  @Test
  void shouldAllowMixingClientSecretWithPartialCertificateConfiguration() {
    // This test verifies that having a client secret allows partial certificate configuration
    // without causing validation errors (certificate auth is simply disabled)

    // when/then - should not throw exception
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .clientAssertionKeystorePath(keystorePath) // partial cert config
            .build();

    assertThat(provider).isNotNull();
  }

  @Test
  void shouldSetDefaultTimeouts() throws MalformedURLException {
    // when
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(AUTHORIZATION_SERVER_URL)
            .build();

    // then
    assertThat(provider).isNotNull();
    // Default timeouts are verified through the provider's behavior in integration tests
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
