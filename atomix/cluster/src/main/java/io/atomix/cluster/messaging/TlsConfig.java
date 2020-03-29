/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.messaging;

import java.io.File;

/** TLS configuration. */
public class TlsConfig {
  private static final String CONFIG_DIR = "/conf";
  private static final String KEYSTORE_FILE_NAME = "atomix.jks";
  private static final File DEFAULT_KEYSTORE_FILE = new File(CONFIG_DIR, KEYSTORE_FILE_NAME);
  private static final String DEFAULT_KEYSTORE_PASSWORD = "changeit";

  private boolean enabled =
      Boolean.parseBoolean(System.getProperty("io.atomix.enableNettyTLS", Boolean.toString(false)));
  private String keyStore =
      System.getProperty("javax.net.ssl.keyStore", DEFAULT_KEYSTORE_FILE.toString());
  private String trustStore =
      System.getProperty("javax.net.ssl.trustStore", DEFAULT_KEYSTORE_FILE.toString());
  private String keyStorePassword =
      System.getProperty("javax.net.ssl.keyStorePassword", DEFAULT_KEYSTORE_PASSWORD);
  private String trustStorePassword =
      System.getProperty("javax.net.ssl.trustStorePassword", DEFAULT_KEYSTORE_PASSWORD);

  /**
   * Returns whether TLS is enabled.
   *
   * @return indicates whether TLS is enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether TLS is enabled.
   *
   * @param enabled whether TLS is enabled
   * @return the TLS configuration
   */
  public TlsConfig setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Returns the key store path.
   *
   * @return the key store path
   */
  public String getKeyStore() {
    return keyStore;
  }

  /**
   * Sets the key store path.
   *
   * @param keyStore the key store path
   * @return the TLS configuration
   */
  public TlsConfig setKeyStore(final String keyStore) {
    this.keyStore = keyStore;
    return this;
  }

  /**
   * Returns the trust store path.
   *
   * @return the trust store path
   */
  public String getTrustStore() {
    return trustStore;
  }

  /**
   * Sets the trust store path.
   *
   * @param trustStore the trust store path
   * @return the TLS configuration
   */
  public TlsConfig setTrustStore(final String trustStore) {
    this.trustStore = trustStore;
    return this;
  }

  /**
   * Returns the key store password.
   *
   * @return the key store password
   */
  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  /**
   * Sets the key store password.
   *
   * @param keyStorePassword the key store password
   * @return the TLS configuration
   */
  public TlsConfig setKeyStorePassword(final String keyStorePassword) {
    this.keyStorePassword = keyStorePassword;
    return this;
  }

  /**
   * Returns the trust store password.
   *
   * @return the trust store password
   */
  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  /**
   * Sets the trust store password.
   *
   * @param trustStorePassword the trust store password
   * @return the TLS configuration
   */
  public TlsConfig setTrustStorePassword(final String trustStorePassword) {
    this.trustStorePassword = trustStorePassword;
    return this;
  }
}
