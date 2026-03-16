/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.config;

/**
 * Immutable configuration for private_key_jwt client assertion. Holds keystore location,
 * credentials, and kid (Key ID) generation settings.
 */
public record AssertionConfig(
    String keystorePath,
    String keystorePassword,
    String keyAlias,
    String keyPassword,
    KidSource kidSource,
    KidDigestAlgorithm kidDigestAlgorithm,
    KidEncoding kidEncoding,
    KidCase kidCase) {

  public enum KidSource {
    CERTIFICATE,
    PUBLIC_KEY
  }

  public enum KidDigestAlgorithm {
    SHA1,
    SHA256
  }

  public enum KidEncoding {
    HEX,
    BASE64URL
  }

  public enum KidCase {
    UPPER,
    LOWER
  }

  public AssertionConfig {
    if (kidSource == null) {
      kidSource = KidSource.PUBLIC_KEY;
    }
    if (kidDigestAlgorithm == null) {
      kidDigestAlgorithm = KidDigestAlgorithm.SHA256;
    }
    if (kidEncoding == null) {
      kidEncoding = KidEncoding.BASE64URL;
    }
  }

  /** Validates that the configuration is internally consistent. */
  public void validate() {
    if (kidCase != null && kidEncoding != KidEncoding.HEX) {
      throw new IllegalStateException("kidCase can only be set when kidEncoding is HEX");
    }
  }

  /** Returns true if this assertion config has a keystore path configured. */
  public boolean isConfigured() {
    return keystorePath != null && !keystorePath.isBlank();
  }
}
