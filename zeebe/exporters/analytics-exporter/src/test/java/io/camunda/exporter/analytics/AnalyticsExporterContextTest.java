/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AnalyticsExporterContextTest {

  @Test
  void shouldComputeDeterministicFingerprint() {
    // given / when
    final var ctx1 = AnalyticsExporterContext.create("test-license", "cluster-1", 1);
    final var ctx2 = AnalyticsExporterContext.create("test-license", "cluster-1", 1);

    // then
    assertThat(ctx1.fingerprint())
        .isEqualTo(ctx2.fingerprint())
        .hasSize(64)
        .matches("[0-9a-f]{64}");
  }

  @Test
  void shouldProduceDifferentFingerprintsForDifferentLicenses() {
    // given / when
    final var ctx1 = AnalyticsExporterContext.create("license-a", "cluster-1", 1);
    final var ctx2 = AnalyticsExporterContext.create("license-b", "cluster-1", 1);

    // then
    assertThat(ctx1.fingerprint()).isNotEqualTo(ctx2.fingerprint());
  }

  @Test
  void shouldRejectMissingLicenseKey() {
    // when / then
    assertThatThrownBy(() -> AnalyticsExporterContext.create(null, "cluster-1", 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CAMUNDA_LICENSE_KEY");
  }

  @Test
  void shouldRejectBlankLicenseKey() {
    // when / then
    assertThatThrownBy(() -> AnalyticsExporterContext.create("  ", "cluster-1", 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CAMUNDA_LICENSE_KEY");
  }

  @Test
  void shouldComputeVerifiableSignature() {
    // given
    final var licenseKey = "test-license";
    final var clusterId = "cluster-1";
    final var ctx = AnalyticsExporterContext.create(licenseKey, clusterId, 1);

    // when
    final var headers = ctx.computeSignatureHeaders();

    // then — independently recompute the HMAC to verify correctness
    final var timestamp = headers.get(AnalyticsExporterContext.HEADER_TIMESTAMP);
    final var signature = headers.get(AnalyticsExporterContext.HEADER_SIGNATURE);
    assertThat(timestamp).matches("\\d+");
    assertThat(signature).matches("[0-9a-f]{64}");

    final var canonical = ctx.fingerprint() + "|" + clusterId + "|" + timestamp;
    final var expected = hmacSha256(licenseKey, canonical);
    assertThat(signature).isEqualTo(expected);
  }

  @Test
  void shouldProduceDifferentSignaturesForDifferentLicenses() {
    // given
    final var ctx1 = AnalyticsExporterContext.create("license-a", "cluster-1", 1);
    final var ctx2 = AnalyticsExporterContext.create("license-b", "cluster-1", 1);

    // when
    final var sig1 = ctx1.computeSignatureHeaders().get(AnalyticsExporterContext.HEADER_SIGNATURE);
    final var sig2 = ctx2.computeSignatureHeaders().get(AnalyticsExporterContext.HEADER_SIGNATURE);

    // then
    assertThat(sig1).isNotEqualTo(sig2);
  }

  @Test
  void shouldRedactSensitiveFieldsInToString() {
    // given
    final var ctx = AnalyticsExporterContext.create("secret-license", "cluster-1", 1);

    // then
    assertThat(ctx.toString())
        .contains("cluster-1")
        .doesNotContain(ctx.fingerprint())
        .doesNotContain("secret-license");
  }

  /** Independent HMAC computation for test verification — must match production algorithm. */
  private static String hmacSha256(final String key, final String data) {
    try {
      final var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (final Exception e) {
      throw new AssertionError("HMAC computation failed", e);
    }
  }
}
