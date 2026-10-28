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
package io.camunda.client.spring.properties;

/**
 * Mutual TLS (mTLS) material for the cluster connection (gRPC and REST), using the same {@code
 * CAMUNDA_MTLS_*} configuration names as the other Camunda SDKs.
 *
 * <p>This configures the cluster transport only. It is independent of the OAuth token endpoint
 * keystore ({@code camunda.client.auth.keystore-*}), which is never reused for the cluster
 * connection, and vice versa.
 *
 * <p>Secrets (key, passphrase, passwords) are never included in {@link #toString()}.
 */
public class CamundaClientMtlsProperties {

  /**
   * The path to the PEM file with the client certificate chain used for mutual TLS against the
   * cluster.
   */
  private String certPath;

  /** The PEM encoded client certificate chain, inline. Alternative to <code>cert-path</code>. */
  private String cert;

  /**
   * The path to the PEM file with the client private key (PKCS#8 encrypted or not, PKCS#1, or EC).
   */
  private String keyPath;

  /** The PEM encoded client private key, inline. Alternative to <code>key-path</code>. */
  private String key;

  /** The optional passphrase of an encrypted client private key. */
  private String keyPassphrase;

  /**
   * The path to a PEM CA bundle used to verify the cluster's server certificate. Mutually exclusive
   * with <code>camunda.client.ca-certificate-path</code> and <code>truststore-path
   * </code>.
   */
  private String caPath;

  /** The PEM encoded CA bundle, inline. Alternative to <code>ca-path</code>. */
  private String ca;

  /**
   * The path to a Java keystore (PKCS#12 or JKS, selected by file extension) holding the client
   * identity. Mutually exclusive with the PEM identity (<code>cert-path</code>/<code>cert</code>
   * and <code>key-path</code>/<code>key</code>).
   */
  private String keystorePath;

  /** The password of the mTLS keystore. */
  private String keystorePassword;

  /**
   * The password of the private key entry in the mTLS keystore. Defaults to the keystore password.
   */
  private String keystoreKeyPassword;

  /**
   * The path to a Java truststore (PKCS#12 or JKS, selected by file extension) with the CA
   * certificates used to verify the cluster's server certificate.
   */
  private String truststorePath;

  /** The password of the mTLS truststore. */
  private String truststorePassword;

  public String getCertPath() {
    return certPath;
  }

  public void setCertPath(final String certPath) {
    this.certPath = certPath;
  }

  public String getCert() {
    return cert;
  }

  public void setCert(final String cert) {
    this.cert = cert;
  }

  public String getKeyPath() {
    return keyPath;
  }

  public void setKeyPath(final String keyPath) {
    this.keyPath = keyPath;
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public String getKeyPassphrase() {
    return keyPassphrase;
  }

  public void setKeyPassphrase(final String keyPassphrase) {
    this.keyPassphrase = keyPassphrase;
  }

  public String getCaPath() {
    return caPath;
  }

  public void setCaPath(final String caPath) {
    this.caPath = caPath;
  }

  public String getCa() {
    return ca;
  }

  public void setCa(final String ca) {
    this.ca = ca;
  }

  public String getKeystorePath() {
    return keystorePath;
  }

  public void setKeystorePath(final String keystorePath) {
    this.keystorePath = keystorePath;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public void setKeystorePassword(final String keystorePassword) {
    this.keystorePassword = keystorePassword;
  }

  public String getKeystoreKeyPassword() {
    return keystoreKeyPassword;
  }

  public void setKeystoreKeyPassword(final String keystoreKeyPassword) {
    this.keystoreKeyPassword = keystoreKeyPassword;
  }

  public String getTruststorePath() {
    return truststorePath;
  }

  public void setTruststorePath(final String truststorePath) {
    this.truststorePath = truststorePath;
  }

  public String getTruststorePassword() {
    return truststorePassword;
  }

  public void setTruststorePassword(final String truststorePassword) {
    this.truststorePassword = truststorePassword;
  }

  /** Never includes secret material: no private key, passphrase, or password. */
  @Override
  public String toString() {
    return "CamundaClientMtlsProperties{"
        + "certPath='"
        + certPath
        + '\''
        + ", certInline="
        + (cert != null)
        + ", keyPath='"
        + keyPath
        + '\''
        + ", keyInline="
        + (key != null)
        + ", caPath='"
        + caPath
        + '\''
        + ", caInline="
        + (ca != null)
        + ", keystorePath='"
        + keystorePath
        + '\''
        + ", truststorePath='"
        + truststorePath
        + '\''
        + '}';
  }
}
