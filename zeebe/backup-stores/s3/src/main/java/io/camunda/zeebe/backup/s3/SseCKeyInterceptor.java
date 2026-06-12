/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Attaches SSE-C (server-side encryption with a caller-provided key) headers to every S3 object
 * write and read, allowing backups to be stored in buckets that mandate SSE-C.
 *
 * <p>The same key must be configured for both writing backups and restoring them: S3 does not store
 * the key and cannot return an object without it.
 *
 * <p>Only {@link PutObjectRequest} (create/overwrite) and {@link GetObjectRequest} (read) carry the
 * key. The store does not use copy or multipart uploads, and bucket-level operations
 * (head-bucket/list/delete) do not accept SSE-C parameters, so they are intentionally left
 * untouched.
 */
public final class SseCKeyInterceptor implements ExecutionInterceptor {

  private static final String ALGORITHM = "AES256";

  private final String keyBase64;
  private final String keyMd5Base64;

  /**
   * @param keyBase64 base64-encoded 32-byte AES-256 key, identical to the key the bucket expects.
   */
  public SseCKeyInterceptor(final String keyBase64) {
    this.keyBase64 = keyBase64;
    final byte[] rawKey = Base64.getDecoder().decode(keyBase64);
    if (rawKey.length != 32) {
      throw new IllegalArgumentException(
          "SSE-C key must decode to exactly 32 bytes (AES-256) but was %d bytes."
              .formatted(rawKey.length));
    }
    // S3 expects the MD5 of the raw key bytes, not of the base64 string.
    keyMd5Base64 = Base64.getEncoder().encodeToString(md5(rawKey));
  }

  @Override
  public SdkRequest modifyRequest(
      final Context.ModifyRequest context, final ExecutionAttributes executionAttributes) {
    final SdkRequest request = context.request();
    if (request instanceof final PutObjectRequest put) {
      return put.toBuilder()
          .sseCustomerAlgorithm(ALGORITHM)
          .sseCustomerKey(keyBase64)
          .sseCustomerKeyMD5(keyMd5Base64)
          .build();
    }
    if (request instanceof final GetObjectRequest get) {
      return get.toBuilder()
          .sseCustomerAlgorithm(ALGORITHM)
          .sseCustomerKey(keyBase64)
          .sseCustomerKeyMD5(keyMd5Base64)
          .build();
    }
    return request;
  }

  private static byte[] md5(final byte[] input) {
    try {
      return MessageDigest.getInstance("MD5").digest(input);
    } catch (final NoSuchAlgorithmException e) {
      // MD5 is guaranteed to be available on every JVM.
      throw new IllegalStateException("MD5 algorithm not available", e);
    }
  }
}
