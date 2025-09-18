/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import static io.camunda.security.configuration.AssertionConfiguration.KidDigestAlgorithm.*;
import static io.camunda.security.configuration.AssertionConfiguration.KidEncoding.*;
import static io.camunda.security.configuration.AssertionConfiguration.KidSource.*;

public final class AssertionConfiguration {

  private KeystoreConfiguration keystoreConfiguration = new KeystoreConfiguration();
  private KidSource kidSource = PUBLIC_KEY;
  private KidDigestAlgorithm kidDigestAlgorithm = SHA256;
  private KidEncoding kidEncoding = BASE64URL;
  private KidCase kidCase;

  public KeystoreConfiguration getKeystore() {
    return keystoreConfiguration;
  }

  public void setKeystore(final KeystoreConfiguration keystoreConfiguration) {
    this.keystoreConfiguration = keystoreConfiguration;
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

  public void validate() {
    if (kidCase != null && kidEncoding != KidEncoding.HEX) {
      throw new IllegalStateException("kidCase can only be set when kidEncoding is HEX");
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private KeystoreConfiguration keystoreConfiguration = new KeystoreConfiguration();
    private KidSource kidSource;
    private KidDigestAlgorithm kidDigestAlgorithm;
    private KidEncoding kidEncoding;
    private KidCase kidCase;

    public Builder keystoreConfiguration(final KeystoreConfiguration keystoreConfiguration) {
      this.keystoreConfiguration = keystoreConfiguration;
      return this;
    }

    public AssertionConfiguration.Builder kidSource(final KidSource kidSource) {
      this.kidSource = kidSource;
      return this;
    }

    public AssertionConfiguration.Builder kidDigestAlgorithm(
        final KidDigestAlgorithm kidDigestAlgorithm) {
      this.kidDigestAlgorithm = kidDigestAlgorithm;
      return this;
    }

    public AssertionConfiguration.Builder kidEncoding(final KidEncoding kidEncoding) {
      this.kidEncoding = kidEncoding;
      return this;
    }

    public AssertionConfiguration.Builder kidCase(final KidCase kidCase) {
      this.kidCase = kidCase;
      return this;
    }

    public AssertionConfiguration build() {
      final AssertionConfiguration config = new AssertionConfiguration();
      config.setKeystore(keystoreConfiguration);
      if (kidSource != null) {
        config.setKidSource(kidSource);
      }
      if (kidDigestAlgorithm != null) {
        config.setKidDigestAlgorithm(kidDigestAlgorithm);
      }
      if (kidEncoding != null) {
        config.setKidEncoding(kidEncoding);
      }
      if (kidCase != null) {
        config.setKidCase(kidCase);
      }
      config.validate();
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
