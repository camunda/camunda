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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.impl.util.AddressUtil;
import java.net.URI;

/**
 * The mutual TLS material configured for the cluster connection (gRPC and REST), resolved from a
 * {@link CamundaClientConfiguration}.
 *
 * <p>Two independent kinds of material are supported:
 *
 * <ul>
 *   <li>PEM: a client certificate chain and private key (file paths or inline content), plus an
 *       optional CA bundle to trust, using the cross-SDK {@code CAMUNDA_MTLS_*} configuration
 *       names.
 *   <li>Keystores (Java-specific): a PKCS#12 or JKS keystore holding the client identity and/or a
 *       truststore holding the CA certificates.
 * </ul>
 *
 * <p>Configuring both PEM and keystore material for the client identity is a configuration error,
 * as is combining the legacy {@code caCertificatePath} with an mTLS CA bundle or truststore.
 *
 * <p>Secrets (key, passphrase, passwords) are held only as fields used to build SSL contexts; they
 * are never included in {@link #toString()} and must never be logged.
 */
public final class MtlsMaterial {

  private final String certPath;
  private final String cert;
  private final String keyPath;
  private final String key;
  private final String keyPassphrase;
  private final String caPath;
  private final String ca;
  private final String keystorePath;
  private final String keystorePassword;
  private final String keystoreKeyPassword;
  private final String truststorePath;
  private final String truststorePassword;
  private final String legacyCaCertificatePath;

  private MtlsMaterial(final Builder builder) {
    certPath = builder.certPath;
    cert = builder.cert;
    keyPath = builder.keyPath;
    key = builder.key;
    keyPassphrase = builder.keyPassphrase;
    caPath = builder.caPath;
    ca = builder.ca;
    keystorePath = builder.keystorePath;
    keystorePassword = builder.keystorePassword;
    keystoreKeyPassword = builder.keystoreKeyPassword;
    truststorePath = builder.truststorePath;
    truststorePassword = builder.truststorePassword;
    legacyCaCertificatePath = builder.legacyCaCertificatePath;
  }

  /**
   * Resolves and validates the mTLS material of the given configuration. Never throws on an absent
   * configuration: without any {@code CAMUNDA_MTLS_*} settings the result describes the legacy
   * behavior (only {@link #getLegacyCaCertificatePath()} may be set).
   *
   * @throws IllegalArgumentException on conflicting or incomplete configuration
   */
  public static MtlsMaterial fromConfiguration(final CamundaClientConfiguration config) {
    final MtlsMaterial material =
        new Builder()
            .certPath(config.getMtlsCertPath())
            .cert(config.getMtlsCert())
            .keyPath(config.getMtlsKeyPath())
            .key(config.getMtlsKey())
            .keyPassphrase(config.getMtlsKeyPassphrase())
            .caPath(config.getMtlsCaPath())
            .ca(config.getMtlsCa())
            .keystorePath(config.getMtlsKeystorePath())
            .keystorePassword(config.getMtlsKeystorePassword())
            .keystoreKeyPassword(config.getMtlsKeystoreKeyPassword())
            .truststorePath(config.getMtlsTruststorePath())
            .truststorePassword(config.getMtlsTruststorePassword())
            .legacyCaCertificatePath(config.getCaCertificatePath())
            .build();
    material.validate(config.getGrpcAddress(), config.getRestAddress());
    return material;
  }

  private void validate(final URI grpcAddress, final URI restAddress) {
    if (hasAnyPemIdentity() && keystorePath != null) {
      throw new IllegalArgumentException(
          "Invalid mTLS configuration: both PEM material (CAMUNDA_MTLS_CERT/CAMUNDA_MTLS_KEY) and "
              + "a keystore (CAMUNDA_MTLS_KEYSTORE_PATH) are configured for the client identity. "
              + "Configure only one of them.");
    }
    if (hasAnyPemIdentity() && !hasPemIdentity()) {
      throw new IllegalArgumentException(
          "Invalid mTLS configuration: both a client certificate (CAMUNDA_MTLS_CERT_PATH or "
              + "CAMUNDA_MTLS_CERT) and a private key (CAMUNDA_MTLS_KEY_PATH or CAMUNDA_MTLS_KEY) "
              + "are required for mTLS, but only one of them is configured.");
    }
    if (caPath != null || ca != null) {
      if (truststorePath != null) {
        throw new IllegalArgumentException(
            "Invalid mTLS configuration: both an mTLS CA bundle (CAMUNDA_MTLS_CA_PATH or "
                + "CAMUNDA_MTLS_CA) and a truststore (CAMUNDA_MTLS_TRUSTSTORE_PATH) are "
                + "configured. Configure only one of them.");
      }
      if (legacyCaCertificatePath != null) {
        throw new IllegalArgumentException(
            "Invalid TLS configuration: both the legacy CA certificate "
                + "(CAMUNDA_CA_CERTIFICATE_PATH / camunda.client.security.certpath) and an mTLS CA "
                + "bundle (CAMUNDA_MTLS_CA_PATH / CAMUNDA_MTLS_CA) are configured. Configure only "
                + "one of them.");
      }
    }
    if (legacyCaCertificatePath != null && truststorePath != null) {
      throw new IllegalArgumentException(
          "Invalid TLS configuration: both the legacy CA certificate "
              + "(CAMUNDA_CA_CERTIFICATE_PATH / camunda.client.security.certpath) and an mTLS "
              + "truststore (CAMUNDA_MTLS_TRUSTSTORE_PATH) are configured. Configure only one of "
              + "them.");
    }
    if (isConfigured()
        && AddressUtil.isPlaintextConnection(grpcAddress)
        && AddressUtil.isPlaintextConnection(restAddress)) {
      throw new IllegalArgumentException(
          "Invalid mTLS configuration: mTLS material (CAMUNDA_MTLS_*) is configured, but both the "
              + "gRPC address ("
              + grpcAddress
              + ") and the REST address ("
              + restAddress
              + ") use a plaintext scheme. mTLS requires TLS-enabled (https) addresses.");
    }
  }

  /** Whether any mTLS material (PEM or keystore, identity or trust) is configured. */
  public boolean isConfigured() {
    return hasAnyPemIdentity()
        || hasMtlsTrust()
        || keystorePath != null
        || keyPassphrase != null
        || keystorePassword != null
        || keystoreKeyPassword != null
        || truststorePassword != null;
  }

  /** Whether a complete PEM client identity (certificate and key) is configured. */
  public boolean hasPemIdentity() {
    return (certPath != null || cert != null) && (keyPath != null || key != null);
  }

  private boolean hasAnyPemIdentity() {
    return certPath != null || cert != null || keyPath != null || key != null;
  }

  /** Whether an mTLS CA bundle or truststore is configured. */
  public boolean hasMtlsTrust() {
    return caPath != null || ca != null || truststorePath != null;
  }

  public String getCertPath() {
    return certPath;
  }

  public String getCert() {
    return cert;
  }

  public String getKeyPath() {
    return keyPath;
  }

  public String getKey() {
    return key;
  }

  public String getKeyPassphrase() {
    return keyPassphrase;
  }

  public String getCaPath() {
    return caPath;
  }

  public String getCa() {
    return ca;
  }

  public String getKeystorePath() {
    return keystorePath;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public String getKeystoreKeyPassword() {
    return keystoreKeyPassword;
  }

  public String getTruststorePath() {
    return truststorePath;
  }

  public String getTruststorePassword() {
    return truststorePassword;
  }

  /** The legacy {@code caCertificatePath}, used only when no mTLS trust material is set. */
  public String getLegacyCaCertificatePath() {
    return legacyCaCertificatePath;
  }

  /**
   * Never includes secret material: no private key, passphrase, or password. Paths are not secret
   * and are included to aid debugging.
   */
  @Override
  public String toString() {
    return "MtlsMaterial{"
        + "certPath="
        + certPath
        + ", certInline="
        + (cert != null)
        + ", keyPath="
        + keyPath
        + ", keyInline="
        + (key != null)
        + ", caPath="
        + caPath
        + ", caInline="
        + (ca != null)
        + ", keystorePath="
        + keystorePath
        + ", truststorePath="
        + truststorePath
        + ", legacyCaCertificatePath="
        + legacyCaCertificatePath
        + '}';
  }

  public static final class Builder {
    private String certPath;
    private String cert;
    private String keyPath;
    private String key;
    private String keyPassphrase;
    private String caPath;
    private String ca;
    private String keystorePath;
    private String keystorePassword;
    private String keystoreKeyPassword;
    private String truststorePath;
    private String truststorePassword;
    private String legacyCaCertificatePath;

    public Builder certPath(final String certPath) {
      this.certPath = emptyToNull(certPath);
      return this;
    }

    public Builder cert(final String cert) {
      this.cert = emptyToNull(cert);
      return this;
    }

    public Builder keyPath(final String keyPath) {
      this.keyPath = emptyToNull(keyPath);
      return this;
    }

    public Builder key(final String key) {
      this.key = emptyToNull(key);
      return this;
    }

    public Builder keyPassphrase(final String keyPassphrase) {
      // Assigned directly (no empty-to-null normalization): an explicitly empty passphrase is a
      // valid, distinct passphrase for an encrypted key and must be preserved.
      this.keyPassphrase = keyPassphrase;
      return this;
    }

    public Builder caPath(final String caPath) {
      this.caPath = emptyToNull(caPath);
      return this;
    }

    public Builder ca(final String ca) {
      this.ca = emptyToNull(ca);
      return this;
    }

    public Builder keystorePath(final String keystorePath) {
      this.keystorePath = emptyToNull(keystorePath);
      return this;
    }

    public Builder keystorePassword(final String keystorePassword) {
      // Assigned directly (no empty-to-null normalization): an explicitly empty store password is
      // a valid, distinct password (PKCS#12 stores can lose encrypted entries when loaded with
      // null) and must be preserved.
      this.keystorePassword = keystorePassword;
      return this;
    }

    public Builder keystoreKeyPassword(final String keystoreKeyPassword) {
      // Assigned directly (no empty-to-null normalization): the key-entry password falls back to
      // the keystore password only when null; an explicitly empty password is a valid, distinct
      // password that must be preserved.
      this.keystoreKeyPassword = keystoreKeyPassword;
      return this;
    }

    public Builder truststorePath(final String truststorePath) {
      this.truststorePath = emptyToNull(truststorePath);
      return this;
    }

    public Builder truststorePassword(final String truststorePassword) {
      // Assigned directly (no empty-to-null normalization): an explicitly empty store password is
      // a valid, distinct password and must be preserved.
      this.truststorePassword = truststorePassword;
      return this;
    }

    public Builder legacyCaCertificatePath(final String legacyCaCertificatePath) {
      this.legacyCaCertificatePath = emptyToNull(legacyCaCertificatePath);
      return this;
    }

    public MtlsMaterial build() {
      return new MtlsMaterial(this);
    }

    private static String emptyToNull(final String value) {
      return value == null || value.isEmpty() ? null : value;
    }
  }
}
