/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.io.FileInputStream;
import java.security.KeyStore;

public final class AssertionKeystoreConfiguration {
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
    try (final FileInputStream fis = new FileInputStream(path)) {
      keyStore.load(fis, password.toCharArray());
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

    public AssertionKeystoreConfiguration.Builder path(final String path) {
      this.path = path;
      return this;
    }

    public AssertionKeystoreConfiguration.Builder password(final String password) {
      this.password = password;
      return this;
    }

    public AssertionKeystoreConfiguration.Builder keyAlias(final String keyAlias) {
      this.keyAlias = keyAlias;
      return this;
    }

    public AssertionKeystoreConfiguration.Builder keyPassword(final String keyPassword) {
      this.keyPassword = keyPassword;
      return this;
    }

    public AssertionKeystoreConfiguration build() {
      final AssertionKeystoreConfiguration config = new AssertionKeystoreConfiguration();
      config.setPath(path);
      config.setPassword(password);
      config.setKeyAlias(keyAlias);
      config.setKeyPassword(keyPassword);
      return config;
    }
  }
}
