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
  private KidSource kidSource;
  private KidDigestAlgorithm kidDigestAlgorithm;
  private KidEncoding kidEncoding;
  private KidCase kidCase;

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

  public KidSource getKidSource() {
    return kidSource;
  }

  public void setKidSource(final KidSource kidSource) {
    this.kidSource = kidSource;
  }

  public KidDigestAlgorithm getKidDigestAlgorithm() {
    return kidDigestAlgorithm;
  }

  public void setKidDigestAlgorithm(final KidDigestAlgorithm kidDigestAlgorithm) {
    this.kidDigestAlgorithm = kidDigestAlgorithm;
  }

  public KidEncoding getKidEncoding() {
    return kidEncoding;
  }

  public void setKidEncoding(final KidEncoding kidEncoding) {
    this.kidEncoding = kidEncoding;
  }

  public KidCase getKidCase() {
    return kidCase;
  }

  public void setKidCase(final KidCase kidCase) {
    this.kidCase = kidCase;
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
    private KidSource kidSource;
    private KidDigestAlgorithm kidDigestAlgorithm;
    private KidEncoding kidEncoding;
    private KidCase kidCase;

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

    public AssertionKeystoreConfiguration.Builder kidSource(final KidSource kidSource) {
      this.kidSource = kidSource;
      return this;
    }

    public AssertionKeystoreConfiguration.Builder kidDigestAlgorithm(
        final KidDigestAlgorithm kidDigestAlgorithm) {
      this.kidDigestAlgorithm = kidDigestAlgorithm;
      return this;
    }

    public AssertionKeystoreConfiguration.Builder kidEncoding(final KidEncoding kidEncoding) {
      this.kidEncoding = kidEncoding;
      return this;
    }

    public AssertionKeystoreConfiguration.Builder kidCase(final KidCase kidCase) {
      this.kidCase = kidCase;
      return this;
    }

    public AssertionKeystoreConfiguration build() {
      final AssertionKeystoreConfiguration config = new AssertionKeystoreConfiguration();
      config.setPath(path);
      config.setPassword(password);
      config.setKeyAlias(keyAlias);
      config.setKeyPassword(keyPassword);
      config.setKidSource(kidSource);
      config.setKidDigestAlgorithm(kidDigestAlgorithm);
      config.setKidEncoding(kidEncoding);
      config.setKidCase(kidCase);
      return config;
    }
  }

  /**
   * Defines if the <code>kid</code> will be generated from the certificate or the certificate's
   * public key.
   */
  public enum KidSource {
    CERTIFICATE,
    PUBLIC_KEY
  }

  /** Defines which digest algorithm will be used on the source to generate the <code>kid</code>. */
  public enum KidDigestAlgorithm {
    SHA1,
    SHA256
  }

  /**
   * If applicable, the <code>kid</code> string will be converted to either uppercase or lowercase.
   * Relevant for hex encoding.
   */
  public enum KidCase {
    UPPER,
    LOWER
  }

  /** Defines the encoding of thd digest bytes into the <code>kid</code> string. */
  public enum KidEncoding {
    HEX,
    BASE64URL
  }
}
