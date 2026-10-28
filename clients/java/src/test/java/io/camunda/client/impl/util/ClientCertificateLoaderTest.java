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
package io.camunda.client.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.netty.handler.ssl.SslContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientCertificateLoaderTest {

  private static final URI SECURE_GRPC = URI.create("https://localhost:26500");
  private static final URI SECURE_REST = URI.create("https://localhost:8080");
  private static final URI PLAINTEXT_GRPC = URI.create("http://localhost:26500");

  private final MtlsTestCertificates certificates = new MtlsTestCertificates();

  @TempDir Path tempDir;

  private CamundaClientConfiguration config(final Consumer<CamundaClientBuilderImpl> customizer) {
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.applyEnvironmentVariableOverrides(false);
    builder.grpcAddress(SECURE_GRPC);
    builder.restAddress(SECURE_REST);
    customizer.accept(builder);
    return builder;
  }

  private Path write(final String name, final String content) {
    try {
      return Files.write(tempDir.resolve(name), content.getBytes());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path write(final String name, final byte[] content) {
    try {
      return Files.write(tempDir.resolve(name), content);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Nested
  class NoMtlsConfigured {

    @Test
    void shouldReturnNullNettyContextWhenNothingConfigured() {
      // given
      final CamundaClientConfiguration config = config(b -> {});

      // when / then
      assertThat(ClientCertificateLoader.isMtlsConfigured(config)).isFalse();
      assertThat(ClientCertificateLoader.createNettySslContext(config)).isNull();
    }

    @Test
    void shouldKeepLegacyCaCertificatePathWorking() {
      // given
      final Path caPath = write("ca.pem", MtlsTestCertificates.toPem(certificates.caCertificate));
      final CamundaClientConfiguration config = config(b -> b.caCertificatePath(caPath.toString()));

      // when / then
      assertThat(ClientCertificateLoader.isMtlsConfigured(config)).isFalse();
      assertThat(ClientCertificateLoader.createNettySslContext(config)).isNotNull();
      assertThat(ClientCertificateLoader.createRestSslContext(config)).isNotNull();
    }
  }

  @Nested
  class PemLoading {

    @Test
    void shouldLoadPemFromPaths() {
      // given
      final Path certPath =
          write("client.pem", MtlsTestCertificates.toPem(certificates.clientCertificate));
      final Path keyPath =
          write(
              "client-key.pem",
              MtlsTestCertificates.toPkcs8Pem(certificates.clientKeyPair.getPrivate()));
      final Path caPath = write("ca.pem", MtlsTestCertificates.toPem(certificates.caCertificate));
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificatePath(certPath.toString());
                b.clientCertificateKeyPath(keyPath.toString());
                b.clientCertificateCaPath(caPath.toString());
              });

      // when
      final SslContext nettyContext = ClientCertificateLoader.createNettySslContext(config);
      final SSLContext restContext = ClientCertificateLoader.createRestSslContext(config);

      // then
      assertThat(nettyContext).isNotNull();
      assertThat(nettyContext.isClient()).isTrue();
      assertThat(restContext).isNotNull();
    }

    @Test
    void shouldLoadPemFromInlineContent() {
      // given
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificate(MtlsTestCertificates.toPem(certificates.clientCertificate));
                b.clientCertificateKey(
                    MtlsTestCertificates.toPkcs8Pem(certificates.clientKeyPair.getPrivate()));
                b.clientCertificateCa(MtlsTestCertificates.toPem(certificates.caCertificate));
              });

      // when / then
      assertThat(ClientCertificateLoader.createNettySslContext(config)).isNotNull();
      assertThat(ClientCertificateLoader.createRestSslContext(config)).isNotNull();
    }

    @Test
    void shouldLoadEncryptedPkcs8Key() {
      // given
      final String encryptedKey =
          MtlsTestCertificates.toEncryptedPkcs8Pem(
              certificates.clientKeyPair.getPrivate(), "topsecret".toCharArray());
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificate(MtlsTestCertificates.toPem(certificates.clientCertificate));
                b.clientCertificateKey(encryptedKey);
                b.clientCertificateKeyPassphrase("topsecret");
                b.clientCertificateCa(MtlsTestCertificates.toPem(certificates.caCertificate));
              });

      // when / then
      assertThat(ClientCertificateLoader.createNettySslContext(config)).isNotNull();
      assertThat(ClientCertificateLoader.createRestSslContext(config)).isNotNull();
    }

    @Test
    void shouldFailToLoadEncryptedKeyWithoutPassphrase() {
      // given
      final String encryptedKey =
          MtlsTestCertificates.toEncryptedPkcs8Pem(
              certificates.clientKeyPair.getPrivate(), "topsecret".toCharArray());
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificate(MtlsTestCertificates.toPem(certificates.clientCertificate));
                b.clientCertificateKey(encryptedKey);
                b.clientCertificateCa(MtlsTestCertificates.toPem(certificates.caCertificate));
              });

      // when / then
      assertThatThrownBy(() -> ClientCertificateLoader.createRestSslContext(config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("passphrase");
    }

    @Test
    void shouldLoadTraditionalPkcs1RsaKey() {
      // given
      final String pkcs1Key =
          MtlsTestCertificates.toTraditionalPem(certificates.clientKeyPair.getPrivate());
      assertThat(pkcs1Key).contains("BEGIN RSA PRIVATE KEY");
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificate(MtlsTestCertificates.toPem(certificates.clientCertificate));
                b.clientCertificateKey(pkcs1Key);
                b.clientCertificateCa(MtlsTestCertificates.toPem(certificates.caCertificate));
              });

      // when / then
      assertThat(ClientCertificateLoader.createNettySslContext(config)).isNotNull();
      assertThat(ClientCertificateLoader.createRestSslContext(config)).isNotNull();
    }

    @Test
    void shouldLoadEcKey() {
      // given
      final MtlsTestCertificates ecCertificates = new MtlsTestCertificates("EC", 256);
      final String ecKey =
          MtlsTestCertificates.toTraditionalPem(ecCertificates.clientKeyPair.getPrivate());
      assertThat(ecKey).contains("BEGIN EC PRIVATE KEY");
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificate(MtlsTestCertificates.toPem(ecCertificates.clientCertificate));
                b.clientCertificateKey(ecKey);
                b.clientCertificateCa(MtlsTestCertificates.toPem(ecCertificates.caCertificate));
              });

      // when / then
      assertThat(ClientCertificateLoader.createNettySslContext(config)).isNotNull();
      assertThat(ClientCertificateLoader.createRestSslContext(config)).isNotNull();
    }
  }

  @Nested
  class KeystoreLoading {

    @ParameterizedTest
    @ValueSource(strings = {"pkcs12", "jks"})
    void shouldLoadClientIdentityFromKeystore(final String storeType) {
      // given
      final byte[] store =
          "jks".equals(storeType)
              ? MtlsTestCertificates.toJks(
                  certificates.clientKeyPair.getPrivate(),
                  certificates.clientCertificate,
                  certificates.caCertificate,
                  "storepass".toCharArray())
              : MtlsTestCertificates.toPkcs12(
                  certificates.clientKeyPair.getPrivate(),
                  certificates.clientCertificate,
                  certificates.caCertificate,
                  "storepass".toCharArray());
      final Path keystorePath = write("client." + storeType, store);
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificateKeystorePath(keystorePath.toString());
                b.clientCertificateKeystorePassword("storepass");
              });

      // when / then
      assertThat(ClientCertificateLoader.createNettySslContext(config)).isNotNull();
      assertThat(ClientCertificateLoader.createRestSslContext(config)).isNotNull();
    }

    @Test
    void shouldLoadTruststore() {
      // given
      final Path truststorePath =
          write(
              "truststore.p12",
              MtlsTestCertificates.toTruststore(
                  certificates.caCertificate, "trustpass".toCharArray()));
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificateTruststorePath(truststorePath.toString());
                b.clientCertificateTruststorePassword("trustpass");
              });

      // when / then
      assertThat(ClientCertificateLoader.createNettySslContext(config)).isNotNull();
      assertThat(ClientCertificateLoader.createRestSslContext(config)).isNotNull();
    }
  }

  @Nested
  class Validation {

    @Test
    void shouldFailWhenMtlsConfiguredOnPlaintextAddress() {
      // given
      final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
      builder.applyEnvironmentVariableOverrides(false);
      builder.grpcAddress(PLAINTEXT_GRPC);
      builder.clientCertificate(MtlsTestCertificates.toPem(certificates.clientCertificate));
      builder.clientCertificateKey(
          MtlsTestCertificates.toPkcs8Pem(certificates.clientKeyPair.getPrivate()));

      // when / then
      assertThatThrownBy(() -> ClientCertificateLoader.validate(builder, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("plaintext");
    }

    @Test
    void shouldFailWhenPemAndKeystoreIdentityAreBothConfigured() {
      // given
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificate(MtlsTestCertificates.toPem(certificates.clientCertificate));
                b.clientCertificateKey(
                    MtlsTestCertificates.toPkcs8Pem(certificates.clientKeyPair.getPrivate()));
                b.clientCertificateKeystorePath("/some/keystore.p12");
              });

      // when / then
      assertThatThrownBy(() -> ClientCertificateLoader.validate(config, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("both");
    }

    @Test
    void shouldFailWhenOnlyCertificateIsConfigured() {
      // given
      final CamundaClientConfiguration config =
          config(
              b -> b.clientCertificate(MtlsTestCertificates.toPem(certificates.clientCertificate)));

      // when / then
      assertThatThrownBy(() -> ClientCertificateLoader.validate(config, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("private key");
    }

    @Test
    void shouldFailWhenOnlyKeyIsConfigured() {
      // given
      final CamundaClientConfiguration config =
          config(
              b ->
                  b.clientCertificateKey(
                      MtlsTestCertificates.toPkcs8Pem(certificates.clientKeyPair.getPrivate())));

      // when / then
      assertThatThrownBy(() -> ClientCertificateLoader.validate(config, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("certificate");
    }

    @Test
    void shouldFailWhenCertPathAndInlineCertAreBothConfigured() {
      // given
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificatePath("/some/cert.pem");
                b.clientCertificate(MtlsTestCertificates.toPem(certificates.clientCertificate));
                b.clientCertificateKey(
                    MtlsTestCertificates.toPkcs8Pem(certificates.clientKeyPair.getPrivate()));
              });

      // when / then
      assertThatThrownBy(() -> ClientCertificateLoader.validate(config, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("both");
    }

    @Test
    void shouldFailWhenPemCaAndTruststoreAreBothConfigured() {
      // given
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificateCaPath("/some/ca.pem");
                b.clientCertificateTruststorePath("/some/truststore.p12");
              });

      // when / then
      assertThatThrownBy(() -> ClientCertificateLoader.validate(config, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("both");
    }

    @Test
    void shouldFailWhenMtlsCaAndCaCertificatePathAreBothConfigured() {
      // given
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.caCertificatePath("/some/legacy-ca.pem");
                b.clientCertificateCa(MtlsTestCertificates.toPem(certificates.caCertificate));
              });

      // when / then
      assertThatThrownBy(() -> ClientCertificateLoader.validate(config, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("caCertificatePath");
    }

    @Test
    void shouldPassValidationForConsistentConfiguration() {
      // given
      final CamundaClientConfiguration config =
          config(
              b -> {
                b.clientCertificate(MtlsTestCertificates.toPem(certificates.clientCertificate));
                b.clientCertificateKey(
                    MtlsTestCertificates.toPkcs8Pem(certificates.clientKeyPair.getPrivate()));
                b.clientCertificateCa(MtlsTestCertificates.toPem(certificates.caCertificate));
              });

      // when / then
      assertThatCode(() -> ClientCertificateLoader.validate(config, true))
          .doesNotThrowAnyException();
      assertThatCode(() -> ClientCertificateLoader.validate(config, false))
          .doesNotThrowAnyException();
    }
  }
}
