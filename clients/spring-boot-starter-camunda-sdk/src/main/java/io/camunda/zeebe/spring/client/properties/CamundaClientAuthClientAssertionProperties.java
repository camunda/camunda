/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.spring.client.properties;

import java.nio.file.Path;

public class CamundaClientAuthClientAssertionProperties {

  /** Path to the keystore where the client assertion certificate is stored */
  private Path keystorePath;

  /** Password of the referenced keystore */
  private String keystorePassword;

  /**
   * Alias of the key holding the certificate to sign the client assertion certificate. If not set,
   * the first alias from the keystore will be used
   */
  private String keystoreKeyAlias;

  /**
   * Password of the key the alias points to. If not set, the password of the keystore will be used
   */
  private String keystoreKeyPassword;

  public Path getKeystorePath() {
    return keystorePath;
  }

  public void setKeystorePath(final Path keystorePath) {
    this.keystorePath = keystorePath;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public void setKeystorePassword(final String keystorePassword) {
    this.keystorePassword = keystorePassword;
  }

  public String getKeystoreKeyAlias() {
    return keystoreKeyAlias;
  }

  public void setKeystoreKeyAlias(final String keystoreKeyAlias) {
    this.keystoreKeyAlias = keystoreKeyAlias;
  }

  public String getKeystoreKeyPassword() {
    return keystoreKeyPassword;
  }

  public void setKeystoreKeyPassword(final String keystoreKeyPassword) {
    this.keystoreKeyPassword = keystoreKeyPassword;
  }
}
