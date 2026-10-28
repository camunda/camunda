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
package io.camunda.client.impl.tls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.netty.handler.ssl.SslContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsContextsTest {

  private static final URI TLS_GRPC = URI.create("https://localhost:26500");
  private static final URI TLS_REST = URI.create("https://localhost:8080");

  private static File resource(final String name) {
    return new File("src/test/resources/mtls/" + name);
  }

  private static String resourcePath(final String name) {
    return resource(name).getAbsolutePath();
  }

  private static String readResource(final String name) throws Exception {
    return new String(Files.readAllBytes(resource(name).toPath()), StandardCharsets.UTF_8);
  }

  private static CamundaClientBuilderImpl tlsConfig() {
    return (CamundaClientBuilderImpl)
        new CamundaClientBuilderImpl()
            .applyEnvironmentVariableOverrides(false)
            .grpcAddress(TLS_GRPC)
            .restAddress(TLS_REST);
  }

  @Test
  void shouldParseUnencryptedPkcs8RsaKey() throws Exception {
    // given
    try (final FileInputStream key = new FileInputStream(resource("client-pkcs8.key"))) {
      // when
      final PrivateKey privateKey = TlsContexts.parsePemPrivateKey(key, null);

      // then
      assertThat(privateKey).isInstanceOf(RSAPrivateKey.class);
    }
  }

  @Test
  void shouldParseEncryptedPkcs8Key() throws Exception {
    // given
    try (final FileInputStream key = new FileInputStream(resource("client-encrypted.key"))) {
      // when
      final PrivateKey privateKey = TlsContexts.parsePemPrivateKey(key, "test1234");

      // then
      assertThat(privateKey).isInstanceOf(RSAPrivateKey.class);
    }
  }

  @Test
  void shouldFailParsingEncryptedKeyWithoutPassphrase() {
    // given / when / then
    assertThatThrownBy(
            () -> {
              try (final FileInputStream key =
                  new FileInputStream(resource("client-encrypted.key"))) {
                TlsContexts.parsePemPrivateKey(key, null);
              }
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CAMUNDA_MTLS_KEY_PASSPHRASE");
  }

  @Test
  void shouldParsePkcs1RsaKey() throws Exception {
    // given
    try (final FileInputStream key = new FileInputStream(resource("client-pkcs1.key"))) {
      // when
      final PrivateKey privateKey = TlsContexts.parsePemPrivateKey(key, null);

      // then
      assertThat(privateKey).isInstanceOf(RSAPrivateKey.class);
    }
  }

  @Test
  void shouldParseEncryptedPkcs1RsaKey() throws Exception {
    // given
    try (final FileInputStream key = new FileInputStream(resource("client-pkcs1-encrypted.key"))) {
      // when
      final PrivateKey privateKey = TlsContexts.parsePemPrivateKey(key, "test1234");

      // then
      assertThat(privateKey).isInstanceOf(RSAPrivateKey.class);
    }
  }

  @Test
  void shouldParseSec1EcKey() throws Exception {
    // given
    try (final FileInputStream key = new FileInputStream(resource("client-ec-sec1.key"))) {
      // when
      final PrivateKey privateKey = TlsContexts.parsePemPrivateKey(key, null);

      // then
      assertThat(privateKey).isInstanceOf(ECPrivateKey.class);
    }
  }

  @Test
  void shouldParsePkcs8EcKey() throws Exception {
    // given
    try (final FileInputStream key = new FileInputStream(resource("client-ec.key"))) {
      // when
      final PrivateKey privateKey = TlsContexts.parsePemPrivateKey(key, null);

      // then
      assertThat(privateKey).isInstanceOf(ECPrivateKey.class);
    }
  }

  @Test
  void shouldParseCertificateChain() throws Exception {
    // given
    try (final FileInputStream cert = new FileInputStream(resource("client.crt"))) {
      // when
      final List<java.security.cert.X509Certificate> chain = TlsContexts.parsePemCertificates(cert);

      // then
      assertThat(chain).hasSize(1);
      assertThat(chain.get(0).getSubjectX500Principal().getName()).contains("camunda-client");
    }
  }

  @Test
  void shouldReturnNullGrpcContextWhenNothingIsConfigured() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();

    // when / then
    assertThat(TlsContexts.createGrpcSslContext(config)).isNull();
  }

  @Test
  void shouldBuildGrpcContextWithPemIdentityAndCa() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate(resourcePath("client.crt"), resourcePath("client.key"));
    config.mtlsCaCertificatePath(resourcePath("ca.crt"));

    // when
    final SslContext context = TlsContexts.createGrpcSslContext(config);

    // then
    assertThat(context).isNotNull();
    assertThat(context.isClient()).isTrue();
  }

  @Test
  void shouldBuildGrpcContextWithInlinePem() throws Exception {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificateContent(readResource("client.crt"), readResource("client.key"));
    config.mtlsCaCertificateContent(readResource("ca.crt"));

    // when
    final SslContext context = TlsContexts.createGrpcSslContext(config);

    // then
    assertThat(context).isNotNull();
  }

  @Test
  void shouldBuildGrpcContextWithEncryptedKey() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate(resourcePath("client.crt"), resourcePath("client-encrypted.key"));
    config.clientKeyPassphrase("test1234");
    config.mtlsCaCertificatePath(resourcePath("ca.crt"));

    // when
    final SslContext context = TlsContexts.createGrpcSslContext(config);

    // then
    assertThat(context).isNotNull();
  }

  @Test
  void shouldBuildGrpcContextWithEcKey() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate(resourcePath("client-ec.crt"), resourcePath("client-ec.key"));
    config.mtlsCaCertificatePath(resourcePath("ca.crt"));

    // when
    final SslContext context = TlsContexts.createGrpcSslContext(config);

    // then
    assertThat(context).isNotNull();
  }

  @Test
  void shouldBuildGrpcContextWithPkcs12KeystoreAndTruststore() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientKeystore(resourcePath("client.p12"), "storepass", null);
    config.mtlsTruststore(resourcePath("truststore.p12"), "trustpass");

    // when
    final SslContext context = TlsContexts.createGrpcSslContext(config);

    // then
    assertThat(context).isNotNull();
  }

  @Test
  void shouldBuildGrpcContextWithJksKeystoreAndTruststore() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientKeystore(resourcePath("client.jks"), "storepass", null);
    config.mtlsTruststore(resourcePath("truststore.jks"), "trustpass");

    // when
    final SslContext context = TlsContexts.createGrpcSslContext(config);

    // then
    assertThat(context).isNotNull();
  }

  @Test
  void shouldBuildGrpcContextWithLegacyCaCertificateOnly() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.caCertificatePath(resourcePath("ca.crt"));

    // when
    final SslContext context = TlsContexts.createGrpcSslContext(config);

    // then
    assertThat(context).isNotNull();
  }

  @Test
  void shouldBuildRestContextWithPemIdentityAndCa() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate(resourcePath("client.crt"), resourcePath("client.key"));
    config.mtlsCaCertificatePath(resourcePath("ca.crt"));

    // when
    final SSLContext context = TlsContexts.createRestSslContext(config);

    // then
    assertThat(context).isNotNull();
    assertThat(context.getProtocol()).isEqualTo("TLS");
  }

  @Test
  void shouldBuildRestContextWithKeystore() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientKeystore(resourcePath("client.p12"), "storepass", null);
    config.mtlsTruststore(resourcePath("truststore.p12"), "trustpass");

    // when
    final SSLContext context = TlsContexts.createRestSslContext(config);

    // then
    assertThat(context).isNotNull();
  }

  @Test
  void shouldBuildRestContextWithLegacyCaCertificateOnly() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.caCertificatePath(resourcePath("ca.crt"));

    // when
    final SSLContext context = TlsContexts.createRestSslContext(config);

    // then
    assertThat(context).isNotNull();
  }

  @Test
  void shouldFailFastOnMissingKeyFile() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate(resourcePath("client.crt"), "/does/not/exist.key");
    config.mtlsCaCertificatePath(resourcePath("ca.crt"));

    // when / then
    assertThatThrownBy(() -> TlsContexts.createGrpcSslContext(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("gRPC");
  }

  @Test
  void shouldFailFastOnKeystoreWithoutPrivateKey() {
    // given - the truststore holds only certificates, no private key entry
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientKeystore(resourcePath("truststore.p12"), "trustpass", null);

    // when / then
    assertThatThrownBy(() -> TlsContexts.createGrpcSslContext(config))
        .isInstanceOf(IllegalStateException.class)
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .rootCause()
        .hasMessageContaining("private key");
  }

  @Test
  void shouldFailFastOnWrongKeystorePassword() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientKeystore(resourcePath("client.p12"), "wrong-password", null);

    // when / then
    assertThatThrownBy(() -> TlsContexts.createGrpcSslContext(config))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectNonPemKeyContent(@TempDir final File tempDir) throws Exception {
    // given
    final File bogus = new File(tempDir, "bogus.key");
    Files.write(bogus.toPath(), "not a pem key".getBytes(StandardCharsets.UTF_8));

    // when / then
    assertThatThrownBy(
            () ->
                TlsContexts.parsePemPrivateKey(
                    new ByteArrayInputStream(Files.readAllBytes(bogus.toPath())), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
