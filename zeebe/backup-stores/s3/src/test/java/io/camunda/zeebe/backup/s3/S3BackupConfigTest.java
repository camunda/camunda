/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

final class S3BackupConfigTest {

  // 32 ASCII bytes => a valid AES-256 key once base64-encoded.
  private static final String VALID_SSEC_KEY =
      Base64.getEncoder()
          .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  @Test
  void shouldAcceptValidSsecKey() {
    // when
    final var config = new Builder().withBucketName("bucket").withSsecKey(VALID_SSEC_KEY).build();

    // then
    assertThat(config.ssecKey()).hasValue(VALID_SSEC_KEY);
  }

  @Test
  void shouldBuildWithoutSsecKey() {
    // when
    final var config = new Builder().withBucketName("bucket").build();

    // then
    assertThat(config.ssecKey()).isEmpty();
  }

  @Test
  void shouldRejectSsecKeyThatDoesNotDecodeTo32Bytes() {
    // given
    final var shortKey =
        Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

    // when - then
    assertThatThrownBy(() -> new Builder().withBucketName("bucket").withSsecKey(shortKey).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void shouldRejectSsecKeyThatIsNotValidBase64() {
    // when - then
    assertThatThrownBy(
            () -> new Builder().withBucketName("bucket").withSsecKey("!!! not base64 !!!").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("valid base64");
  }

  @Test
  void shouldNotValidateSsecKeyWhenAbsent() {
    // when - then
    assertThatCode(() -> new Builder().withBucketName("bucket").build()).doesNotThrowAnyException();
  }

  @Test
  void shouldRedactSsecKeyInToString() {
    // given
    final var config = new Builder().withBucketName("bucket").withSsecKey(VALID_SSEC_KEY).build();

    // when
    final var asString = config.toString();

    // then
    assertThat(asString).contains("ssecKey=").doesNotContain(VALID_SSEC_KEY);
  }
}
