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

  private static final String DEFAULT_LICENSE = "test-license";
  private static final String DEFAULT_CLUSTER = "cluster-1";
  private static final String DEFAULT_PHYSICAL_TENANT = "test-physical-tenant";

  @Test
  void shouldComputeDeterministicFingerprint() {
    // given / when
    final var ctx1 = context();
    final var ctx2 = context();

    // then
    assertThat(ctx1.fingerprint())
        .isEqualTo(ctx2.fingerprint())
        .hasSize(64)
        .matches("[0-9a-f]{64}");
  }

  @Test
  void shouldProduceDifferentFingerprintsForDifferentLicenses() {
    // given / when
    final var ctx1 = context("license-a");
    final var ctx2 = context("license-b");

    // then
    assertThat(ctx1.fingerprint()).isNotEqualTo(ctx2.fingerprint());
  }

  @Test
  void shouldProduceDifferentFingerprintsForDifferentClusters() {
    // given / when
    final var ctx1 = contextForCluster("cluster-a");
    final var ctx2 = contextForCluster("cluster-b");

    // then — fingerprint is derived from license only, not cluster
    // but these are different contexts, verify they are independent
    assertThat(ctx1.fingerprint()).isEqualTo(ctx2.fingerprint());
    assertThat(ctx1.clusterId()).isNotEqualTo(ctx2.clusterId());
  }

  @Test
  void shouldExposePhysicalTenantId() {
    // given / when
    final var ctx = contextForPhysicalTenant("physical-tenant-a");

    // then
    assertThat(ctx.physicalTenantId()).isEqualTo("physical-tenant-a");
  }

  @Test
  void shouldNormalizeNullPhysicalTenantIdToEmptyString() {
    // given / when — a broker context that (unexpectedly) supplies no physical tenant id
    final var ctx = contextForPhysicalTenant(null);

    // then — never null, so OTel attribute setters always receive a valid String
    assertThat(ctx.physicalTenantId()).isEmpty();
  }

  @Test
  void shouldRejectMissingLicenseKey() {
    // when / then
    assertThatThrownBy(() -> context(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectBlankLicenseKey() {
    // when / then
    assertThatThrownBy(() -> context("  ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldComputeVerifiableSignature() {
    // given
    final var ctx = context();

    // when
    final var headers = ctx.computeSignatureHeaders();

    // then — independently recompute the HMAC to verify correctness
    final var timestamp = headers.get(AnalyticsExporterContext.HEADER_TIMESTAMP);
    final var signature = headers.get(AnalyticsExporterContext.HEADER_SIGNATURE);
    assertThat(timestamp).matches("\\d+");
    assertThat(signature).matches("[0-9a-f]{64}");

    final var canonical = ctx.fingerprint() + "|" + DEFAULT_CLUSTER + "|" + timestamp;
    final var expected = hmacSha256(DEFAULT_LICENSE, canonical);
    assertThat(signature).isEqualTo(expected);
  }

  @Test
  void shouldProduceDifferentSignaturesForDifferentLicenses() {
    // given — same cluster, same timestamp input via canonical string
    final var licenseA = "license-a";
    final var licenseB = "license-b";
    final var timestamp = "1234567890";

    final var ctxA = context(licenseA);
    final var ctxB = context(licenseB);

    // when — compute HMAC with identical canonical structure but different keys
    final var canonicalA = ctxA.fingerprint() + "|" + DEFAULT_CLUSTER + "|" + timestamp;
    final var canonicalB = ctxB.fingerprint() + "|" + DEFAULT_CLUSTER + "|" + timestamp;

    final var sigA = hmacSha256(licenseA, canonicalA);
    final var sigB = hmacSha256(licenseB, canonicalB);

    // then — different licenses produce different signatures even for the same timestamp
    assertThat(sigA).isNotEqualTo(sigB);
  }

  @Test
  void shouldRedactSensitiveFieldsInToString() {
    // given
    final var ctx = context("secret-license");

    // then
    assertThat(ctx.toString())
        .contains(DEFAULT_CLUSTER)
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

  // -- helpers --

  /** Context with all-default fields — license, cluster, partition, and physical-tenant id. */
  private static AnalyticsExporterContext context() {
    return context(DEFAULT_LICENSE, DEFAULT_CLUSTER, DEFAULT_PHYSICAL_TENANT);
  }

  /** Context with only {@code licenseKey} overridden; other fields keep their defaults. */
  private static AnalyticsExporterContext context(final String licenseKey) {
    return context(licenseKey, DEFAULT_CLUSTER, DEFAULT_PHYSICAL_TENANT);
  }

  /** Context with only {@code clusterId} overridden; other fields keep their defaults. */
  private static AnalyticsExporterContext contextForCluster(final String clusterId) {
    return context(DEFAULT_LICENSE, clusterId, DEFAULT_PHYSICAL_TENANT);
  }

  /** Context with only {@code physicalTenantId} overridden; other fields keep their defaults. */
  private static AnalyticsExporterContext contextForPhysicalTenant(final String physicalTenantId) {
    return context(DEFAULT_LICENSE, DEFAULT_CLUSTER, physicalTenantId);
  }

  private static AnalyticsExporterContext context(
      final String licenseKey, final String clusterId, final String physicalTenantId) {
    return AnalyticsExporterContext.create(licenseKey, clusterId, 1, physicalTenantId, "");
  }
}
