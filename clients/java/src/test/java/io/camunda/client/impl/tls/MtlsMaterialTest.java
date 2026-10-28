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
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.impl.CamundaClientBuilderImpl;
import java.net.URI;
import org.junit.jupiter.api.Test;

class MtlsMaterialTest {

  private static final URI TLS_GRPC = URI.create("https://localhost:26500");
  private static final URI TLS_REST = URI.create("https://localhost:8080");
  private static final URI PLAINTEXT_GRPC = URI.create("http://localhost:26500");
  private static final URI PLAINTEXT_REST = URI.create("http://localhost:8080");

  private static CamundaClientBuilderImpl tlsConfig() {
    return (CamundaClientBuilderImpl)
        new CamundaClientBuilderImpl()
            .applyEnvironmentVariableOverrides(false)
            .grpcAddress(TLS_GRPC)
            .restAddress(TLS_REST);
  }

  @Test
  void shouldNotBeConfiguredByDefault() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();

    // when
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);

    // then
    assertThat(material.isConfigured()).isFalse();
    assertThat(material.hasPemIdentity()).isFalse();
    assertThat(material.hasMtlsTrust()).isFalse();
  }

  @Test
  void shouldResolvePemIdentityFromPaths() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate("/path/to/cert.pem", "/path/to/key.pem");

    // when
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);

    // then
    assertThat(material.isConfigured()).isTrue();
    assertThat(material.hasPemIdentity()).isTrue();
    assertThat(material.getCertPath()).isEqualTo("/path/to/cert.pem");
    assertThat(material.getKeyPath()).isEqualTo("/path/to/key.pem");
  }

  @Test
  void shouldResolvePemIdentityFromInlineContent() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificateContent("CERT PEM", "KEY PEM");

    // when
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);

    // then
    assertThat(material.hasPemIdentity()).isTrue();
    assertThat(material.getCert()).isEqualTo("CERT PEM");
    assertThat(material.getKey()).isEqualTo("KEY PEM");
  }

  @Test
  void shouldFailWhenOnlyCertificateIsConfigured() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate("/path/to/cert.pem", null);

    // when / then
    assertThatThrownBy(() -> MtlsMaterial.fromConfiguration(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CAMUNDA_MTLS_KEY");
  }

  @Test
  void shouldFailWhenOnlyKeyIsConfigured() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate(null, "/path/to/key.pem");

    // when / then
    assertThatThrownBy(() -> MtlsMaterial.fromConfiguration(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CAMUNDA_MTLS_CERT");
  }

  @Test
  void shouldFailWhenPemIdentityAndKeystoreAreBothConfigured() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate("/path/to/cert.pem", "/path/to/key.pem");
    config.clientKeystore("/path/to/keystore.p12", "secret", null);

    // when / then
    assertThatThrownBy(() -> MtlsMaterial.fromConfiguration(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CAMUNDA_MTLS_KEYSTORE_PATH");
  }

  @Test
  void shouldFailWhenLegacyCaCertificateAndMtlsCaAreBothConfigured() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.caCertificatePath("/path/to/legacy-ca.pem");
    config.mtlsCaCertificatePath("/path/to/ca.pem");

    // when / then
    assertThatThrownBy(() -> MtlsMaterial.fromConfiguration(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CAMUNDA_CA_CERTIFICATE_PATH");
  }

  @Test
  void shouldFailWhenLegacyCaCertificateAndTruststoreAreBothConfigured() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.caCertificatePath("/path/to/legacy-ca.pem");
    config.mtlsTruststore("/path/to/truststore.p12", "secret");

    // when / then
    assertThatThrownBy(() -> MtlsMaterial.fromConfiguration(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CAMUNDA_CA_CERTIFICATE_PATH");
  }

  @Test
  void shouldFailWhenCaBundlePathAndTruststoreAreBothConfigured() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.mtlsCaCertificatePath("/path/to/ca.pem");
    config.mtlsTruststore("/path/to/truststore.p12", "secret");

    // when / then
    assertThatThrownBy(() -> MtlsMaterial.fromConfiguration(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CAMUNDA_MTLS_TRUSTSTORE_PATH");
  }

  @Test
  void shouldFailWhenInlineCaBundleAndTruststoreAreBothConfigured() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.mtlsCaCertificateContent("INLINE CA");
    config.mtlsTruststore("/path/to/truststore.p12", "secret");

    // when / then
    assertThatThrownBy(() -> MtlsMaterial.fromConfiguration(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CAMUNDA_MTLS_TRUSTSTORE_PATH");
  }

  @Test
  void shouldPreserveExplicitlyEmptyKeystoreKeyPassword() {
    // given - an empty key password is a valid, distinct password; only null falls back to the
    // keystore password
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientKeystore("/path/to/keystore.p12", "storepass", "");

    // when
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);

    // then
    assertThat(material.getKeystoreKeyPassword()).isEmpty();
    assertThat(material.getKeystorePassword()).isEqualTo("storepass");
  }

  @Test
  void shouldPreserveExplicitlyEmptyKeystorePassword() {
    // given - an empty store password is valid: a PKCS#12 store loaded with null instead of an
    // empty password can silently lose its encrypted entries
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientKeystore("/path/to/keystore.p12", "", null);

    // when
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);

    // then
    assertThat(material.getKeystorePassword()).isEmpty();
  }

  @Test
  void shouldPreserveExplicitlyEmptyTruststorePassword() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.mtlsTruststore("/path/to/truststore.p12", "");

    // when
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);

    // then
    assertThat(material.getTruststorePassword()).isEmpty();
  }

  @Test
  void shouldPreserveExplicitlyEmptyKeyPassphrase() {
    // given - an empty passphrase is a valid, distinct passphrase for an encrypted PEM key
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificate("/path/to/cert.pem", "/path/to/key.pem");
    config.clientKeyPassphrase("");

    // when
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);

    // then
    assertThat(material.getKeyPassphrase()).isEmpty();
  }

  @Test
  void shouldFailWhenMtlsIsConfiguredOnPlaintextAddresses() {
    // given
    final CamundaClientBuilderImpl config =
        (CamundaClientBuilderImpl)
            new CamundaClientBuilderImpl()
                .applyEnvironmentVariableOverrides(false)
                .grpcAddress(PLAINTEXT_GRPC)
                .restAddress(PLAINTEXT_REST);
    config.clientCertificate("/path/to/cert.pem", "/path/to/key.pem");

    // when / then
    assertThatThrownBy(() -> MtlsMaterial.fromConfiguration(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("plaintext");
  }

  @Test
  void shouldAcceptMtlsWhenOnlyOneAddressIsTls() {
    // given - the gRPC address is TLS, the REST address is plaintext (e.g. REST via a sidecar)
    final CamundaClientBuilderImpl config =
        (CamundaClientBuilderImpl)
            new CamundaClientBuilderImpl()
                .applyEnvironmentVariableOverrides(false)
                .grpcAddress(TLS_GRPC)
                .restAddress(PLAINTEXT_REST);
    config.clientCertificate("/path/to/cert.pem", "/path/to/key.pem");

    // when / then
    assertThatNoException().isThrownBy(() -> MtlsMaterial.fromConfiguration(config));
  }

  @Test
  void shouldAcceptKeystoreIdentity() {
    // given
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientKeystore("/path/to/keystore.p12", "storepass", "keypass");

    // when
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);

    // then
    assertThat(material.isConfigured()).isTrue();
    assertThat(material.hasPemIdentity()).isFalse();
    assertThat(material.getKeystorePath()).isEqualTo("/path/to/keystore.p12");
  }

  @Test
  void shouldNotLeakSecretsInToString() {
    // given - non-conflicting trust settings: a CA bundle and a truststore are mutually exclusive
    final CamundaClientBuilderImpl config = tlsConfig();
    config.clientCertificateContent("INLINE CERT", "INLINE KEY");
    config.clientKeyPassphrase("key-secret");
    config.clientKeystore(null, "store-secret", null);
    config.mtlsCaCertificateContent("INLINE CA");

    // when
    final MtlsMaterial material = MtlsMaterial.fromConfiguration(config);
    final String asString = material.toString();

    // then
    assertThat(asString)
        .doesNotContain("INLINE KEY")
        .doesNotContain("INLINE CERT")
        .doesNotContain("INLINE CA")
        .doesNotContain("key-secret")
        .doesNotContain("store-secret");
  }
}
