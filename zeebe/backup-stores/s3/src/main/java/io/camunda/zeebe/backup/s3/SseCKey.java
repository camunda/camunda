/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import java.util.Base64;

/**
 * Validation for SSE-C (server-side encryption with a customer-provided key) key material.
 *
 * <p>Shared by {@link S3BackupConfig}, which fails fast on misconfiguration, and {@link
 * SseCKeyInterceptor}, which additionally needs the decoded key bytes to compute the key MD5.
 */
final class SseCKey {

  private SseCKey() {}

  /**
   * Decodes a base64-encoded SSE-C key and validates that it is exactly 32 bytes (AES-256).
   *
   * @param keyBase64 the base64-encoded key
   * @return the decoded 32 raw key bytes
   * @throws IllegalArgumentException if the key is not valid base64 or does not decode to exactly
   *     32 bytes
   */
  static byte[] decodeAndValidate(final String keyBase64) {
    final byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(keyBase64);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException("SSE-C key must be valid base64.", e);
    }
    if (decoded.length != 32) {
      throw new IllegalArgumentException(
          "SSE-C key must decode to exactly 32 bytes (AES-256) but was %d bytes."
              .formatted(decoded.length));
    }
    return decoded;
  }
}
