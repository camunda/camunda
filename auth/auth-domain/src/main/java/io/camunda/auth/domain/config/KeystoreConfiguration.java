/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.config;

import java.io.FileInputStream;
import java.security.KeyStore;

public class KeystoreConfiguration {
  private String path;
  private String password;
  private String keyAlias;
  private String keyPassword;

  public String getPath() {
    return path;
  }

  public void setPath(final String path) {
    this.path = path;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getKeyAlias() {
    return keyAlias;
  }

  public void setKeyAlias(final String keyAlias) {
    this.keyAlias = keyAlias;
  }

  public String getKeyPassword() {
    return keyPassword;
  }

  public void setKeyPassword(final String keyPassword) {
    this.keyPassword = keyPassword;
  }

  public KeyStore loadKeystore() throws Exception {
    final KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (final FileInputStream fis = new FileInputStream(getPath())) {
      keyStore.load(fis, getPassword().toCharArray());
    }
    return keyStore;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String path;
    private String password;
    private String keyAlias;
    private String keyPassword;

    public Builder path(final String path) {
      this.path = path;
      return this;
    }

    public Builder password(final String password) {
      this.password = password;
      return this;
    }

    public Builder keyAlias(final String keyAlias) {
      this.keyAlias = keyAlias;
      return this;
    }

    public Builder keyPassword(final String keyPassword) {
      this.keyPassword = keyPassword;
      return this;
    }

    public KeystoreConfiguration build() {
      final var config = new KeystoreConfiguration();
      config.setPath(path);
      config.setPassword(password);
      config.setKeyAlias(keyAlias);
      config.setKeyPassword(keyPassword);
      return config;
    }
  }
}
