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
 * Properties for mutual TLS (mTLS) between the Camunda client and the cluster, applied to both the
 * gRPC and the REST transport. These settings are independent of the OAuth token endpoint TLS
 * settings under <code>camunda.client.auth.*</code>: the cluster mTLS certificate is not reused for
 * the OAuth call, and the OAuth certificate is not reused for the cluster connection.
 */
public class CamundaClientMtlsProperties {

  /**
   * The path to the PEM-encoded client certificate chain used for mutual TLS against the cluster.
   * Must be configured together with <code>camunda.client.mtls.key-path</code> or <code>
   * camunda.client.mtls.key</code>, and must not be combined with <code>
   * camunda.client.mtls.cert</code> or <code>camunda.client.mtls.keystore-path</code>.
   */
  private String certPath;

  /**
   * The inline PEM-encoded client certificate chain used for mutual TLS against the cluster.
   * Alternative to <code>camunda.client.mtls.cert-path</code>.
   */
  private String cert;

  /**
   * The path to the PEM-encoded private key of the mTLS client certificate. Supported formats are
   * PKCS#8 (plain or encrypted), PKCS#1 (<code>BEGIN RSA PRIVATE KEY</code>), and SEC1 (<code>BEGIN
   * EC PRIVATE KEY</code>).
   */
  private String keyPath;

  /**
   * The inline PEM-encoded private key of the mTLS client certificate. Alternative to <code>
   * camunda.client.mtls.key-path</code>.
   */
  private String key;

  /** The optional passphrase used to decrypt an encrypted mTLS client private key. */
  private String keyPassphrase;

  /**
   * The path to a PEM CA bundle used to verify the cluster certificate. Must not be combined with
   * <code>camunda.client.mtls.ca</code>, <code>camunda.client.mtls.truststore-path</code>, or
   * <code>camunda.client.ca-certificate-path</code>.
   */
  private String caPath;

  /**
   * The inline PEM CA bundle used to verify the cluster certificate. Alternative to <code>
   * camunda.client.mtls.ca-path</code>.
   */
  private String ca;

  /**
   * The path to a keystore (PKCS#12 or JKS) holding the mTLS client identity (certificate chain
   * and private key). Must not be combined with the PEM client certificate settings.
   */
  private String keystorePath;

  /** The password of the mTLS client identity keystore. */
  private String keystorePassword;

  /**
   * The password of the private key inside the mTLS client identity keystore. Defaults to the
   * keystore password when not set.
   */
  private String keystoreKeyPassword;

  /**
   * The path to a truststore (PKCS#12 or JKS) used to verify the cluster certificate. Must not be
   * combined with <code>camunda.client.mtls.ca-path</code>, <code>camunda.client.mtls.ca</code>, or
   * <code>camunda.client.ca-certificate-path</code>.
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

  @Override
  public String toString() {
    // secrets (key, passphrase, passwords) are deliberately masked
    return "CamundaClientMtlsProperties{"
        + "certPath='"
        + certPath
        + '\''
        + ", cert='"
        + (cert != null ? "***" : null)
        + '\''
        + ", keyPath='"
        + keyPath
        + '\''
        + ", key='"
        + (key != null ? "***" : null)
        + '\''
        + ", keyPassphrase='"
        + (keyPassphrase != null ? "***" : null)
        + '\''
        + ", caPath='"
        + caPath
        + '\''
        + ", ca='"
        + (ca != null ? "***" : null)
        + '\''
        + ", keystorePath='"
        + keystorePath
        + '\''
        + ", keystorePassword='"
        + (keystorePassword != null ? "***" : null)
        + '\''
        + ", keystoreKeyPassword='"
        + (keystoreKeyPassword != null ? "***" : null)
        + '\''
        + ", truststorePath='"
        + truststorePath
        + '\''
        + ", truststorePassword='"
        + (truststorePassword != null ? "***" : null)
        + '\''
        + '}';
  }
}
