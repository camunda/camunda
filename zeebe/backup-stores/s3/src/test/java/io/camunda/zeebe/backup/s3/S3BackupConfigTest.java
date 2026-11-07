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
import io.camunda.zeebe.backup.s3.S3BackupConfig.DefaultsMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class S3BackupConfigTest {

  // 32 ASCII bytes => a valid AES-256 key once base64-encoded.
  private static final String VALID_SSEC_KEY =
      Base64.getEncoder()
          .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  @Test
  void shouldLeaveDefaultsModeUnsetByDefault() {
    // when
    final var config = new Builder().withBucketName("bucket").build();

    // then
    assertThat(config.defaultsMode()).isEqualTo(DefaultsMode.defaultValue());
  }

  @ParameterizedTest
  @EnumSource(DefaultsMode.class)
  void shouldMapDefaultsModeToSdkConstantWithSameName(final DefaultsMode mode) {
    // when / then
    assertThat(mode.toSdk().name()).isEqualTo(mode.name());
  }

  // Our DefaultsMode mirrors the AWS SDK enum minus MOBILE, which tunes the client for mobile apps.
  // The mapping only guards our values against the SDK, so a mode added by an SDK upgrade would go
  // unnoticed. Failing here forces whoever bumps the SDK to decide whether to support the new mode.
  @Test
  void shouldCoverAllSdkDefaultsModesExceptMobile() {
    // given
    final var sdkModes =
        Arrays.stream(software.amazon.awssdk.awscore.defaultsmode.DefaultsMode.values())
            .map(Enum::name)
            .filter(name -> !name.equals("MOBILE"))
            .toList();

    // when / then
    assertThat(Arrays.stream(DefaultsMode.values()).map(Enum::name))
        .containsExactlyInAnyOrderElementsOf(sdkModes);
  }

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
  void shouldSanitizeBasePath() {
    // when
    final var config = new Builder().withBucketName("bucket").withBasePath("/tenant/").build();

    // then
    assertThat(config.basePath()).hasValue("tenant");
  }

  @Test
  void shouldTreatEmptyBasePathAsNone() {
    // when
    final var config = new Builder().withBucketName("bucket").withBasePath("").build();

    // then
    assertThat(config.basePath()).isEmpty();
  }

  @Test
  void shouldRejectBuildWithoutBucketName() {
    // when - then
    assertThatThrownBy(() -> new Builder().build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Bucket name must not be empty.");
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
  void shouldNotLimitReadRateByDefault() {
    // when
    final var config = new Builder().withBucketName("bucket").build();

    // then
    assertThat(config.maxReadRate()).isZero();
  }

  @Test
  void shouldKeepConfiguredMaxReadRate() {
    // when
    final var config = new Builder().withBucketName("bucket").withMaxReadRate(1024).build();

    // then
    assertThat(config.maxReadRate()).isEqualTo(1024);
  }

  @Test
  void shouldRejectNegativeMaxReadRate() {
    // when - then
    assertThatThrownBy(() -> new Builder().withBucketName("bucket").withMaxReadRate(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxReadRate");
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
