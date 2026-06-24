/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.InterceptorContext;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartCopyRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

final class SseCKeyInterceptorTest {

  // 32 ASCII bytes => a valid AES-256 key.
  private static final byte[] RAW_KEY =
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final String KEY_BASE64 = Base64.getEncoder().encodeToString(RAW_KEY);

  private final SseCKeyInterceptor interceptor = new SseCKeyInterceptor(KEY_BASE64);

  @Test
  void shouldAttachSseCHeadersToPutObjectRequest() {
    // given
    final var put = PutObjectRequest.builder().bucket("bucket").key("key").build();

    // when
    final var modified = (PutObjectRequest) modify(put);

    // then
    assertThat(modified.sseCustomerAlgorithm()).isEqualTo("AES256");
    assertThat(modified.sseCustomerKey()).isEqualTo(KEY_BASE64);
    assertThat(modified.sseCustomerKeyMD5()).isEqualTo(expectedKeyMd5Base64());
  }

  @Test
  void shouldAttachSseCHeadersToGetObjectRequest() {
    // given
    final var get = GetObjectRequest.builder().bucket("bucket").key("key").build();

    // when
    final var modified = (GetObjectRequest) modify(get);

    // then
    assertThat(modified.sseCustomerAlgorithm()).isEqualTo("AES256");
    assertThat(modified.sseCustomerKey()).isEqualTo(KEY_BASE64);
    assertThat(modified.sseCustomerKeyMD5()).isEqualTo(expectedKeyMd5Base64());
  }

  @Test
  void shouldComputeKeyMd5FromRawKeyBytesNotFromBase64String() {
    // given
    final var put = PutObjectRequest.builder().bucket("bucket").key("key").build();
    final var md5OfBase64String =
        Base64.getEncoder().encodeToString(md5(KEY_BASE64.getBytes(StandardCharsets.UTF_8)));

    // when
    final var modified = (PutObjectRequest) modify(put);

    // then
    assertThat(modified.sseCustomerKeyMD5())
        .isEqualTo(expectedKeyMd5Base64())
        .isNotEqualTo(md5OfBase64String);
  }

  @Test
  void shouldLeaveListObjectsRequestUntouched() {
    // given
    final var list = ListObjectsV2Request.builder().bucket("bucket").build();

    // when
    final var result = modify(list);

    // then
    assertThat(result).isSameAs(list);
  }

  @Test
  void shouldLeaveDeleteObjectRequestUntouched() {
    // given
    final var delete = DeleteObjectRequest.builder().bucket("bucket").key("key").build();

    // when
    final var result = modify(delete);

    // then
    assertThat(result).isSameAs(delete);
  }

  @Test
  void shouldFailFastOnCreateMultipartUploadRequest() {
    // given
    final var request = CreateMultipartUploadRequest.builder().bucket("bucket").key("key").build();

    // when - then
    assertThatThrownBy(() -> modify(request))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("CreateMultipartUploadRequest")
        .hasMessageContaining("SSE-C");
  }

  @Test
  void shouldFailFastOnUploadPartRequest() {
    // given
    final var request =
        UploadPartRequest.builder()
            .bucket("bucket")
            .key("key")
            .uploadId("id")
            .partNumber(1)
            .build();

    // when - then
    assertThatThrownBy(() -> modify(request))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("UploadPartRequest");
  }

  @Test
  void shouldFailFastOnCompleteMultipartUploadRequest() {
    // given
    final var request =
        CompleteMultipartUploadRequest.builder().bucket("bucket").key("key").uploadId("id").build();

    // when - then
    assertThatThrownBy(() -> modify(request))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("CompleteMultipartUploadRequest");
  }

  @Test
  void shouldFailFastOnCopyObjectRequest() {
    // given
    final var request =
        CopyObjectRequest.builder()
            .sourceBucket("bucket")
            .sourceKey("source")
            .destinationBucket("bucket")
            .destinationKey("dest")
            .build();

    // when - then
    assertThatThrownBy(() -> modify(request))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("CopyObjectRequest");
  }

  @Test
  void shouldFailFastOnUploadPartCopyRequest() {
    // given
    final var request =
        UploadPartCopyRequest.builder()
            .sourceBucket("bucket")
            .sourceKey("source")
            .destinationBucket("bucket")
            .destinationKey("dest")
            .uploadId("id")
            .partNumber(1)
            .build();

    // when - then
    assertThatThrownBy(() -> modify(request))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("UploadPartCopyRequest");
  }

  @Test
  void shouldRejectKeyThatDoesNotDecodeTo32Bytes() {
    // given
    final var shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());

    // when - then
    assertThatThrownBy(() -> new SseCKeyInterceptor(shortKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void shouldRejectInvalidBase64Key() {
    // when - then
    assertThatThrownBy(() -> new SseCKeyInterceptor("!!! not base64 !!!"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private SdkRequest modify(final SdkRequest request) {
    return interceptor.modifyRequest(
        InterceptorContext.builder().request(request).build(), new ExecutionAttributes());
  }

  private static String expectedKeyMd5Base64() {
    return Base64.getEncoder().encodeToString(md5(RAW_KEY));
  }

  private static byte[] md5(final byte[] input) {
    try {
      return MessageDigest.getInstance("MD5").digest(input);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
