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
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartCopyRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

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
 *
 * <p>Copy and multipart requests need a different set of SSE-C parameters (multipart uploads must
 * repeat the key on every part and on the create/complete calls; copies additionally need the
 * source key). Because this interceptor does not populate those, encountering such a request while
 * SSE-C is configured means the store started issuing operations this interceptor cannot secure. To
 * avoid silently writing unencrypted or unreadable objects, those requests fail fast rather than
 * being passed through unmodified.
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
    final byte[] rawKey = SseCKey.decodeAndValidate(keyBase64);
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
    if (request instanceof CreateMultipartUploadRequest
        || request instanceof UploadPartRequest
        || request instanceof CompleteMultipartUploadRequest
        || request instanceof CopyObjectRequest
        || request instanceof UploadPartCopyRequest) {
      throw new UnsupportedOperationException(
          ("SSE-C is configured but the S3 backup store attempted a %s. This interceptor only "
                  + "attaches customer-provided encryption keys to PutObject and GetObject "
                  + "requests; copy and multipart uploads require additional SSE-C parameters that "
                  + "are not implemented. Add SSE-C support for these operations before enabling "
                  + "them on the S3 backup store.")
              .formatted(request.getClass().getSimpleName()));
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
