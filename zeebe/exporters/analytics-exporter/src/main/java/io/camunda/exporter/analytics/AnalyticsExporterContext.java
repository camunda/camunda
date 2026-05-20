/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Context for an analytics exporter instance, derived from the broker context at configure time.
 * Bundles auth credentials (fingerprint) and partition identity (clusterId, partitionId).
 */
record AnalyticsExporterContext(String fingerprint, String clusterId, int partitionId) {

  AnalyticsExporterContext {
    Objects.requireNonNull(fingerprint, "fingerprint");
    Objects.requireNonNull(clusterId, "clusterId");
  }

  /** Redact fingerprint to prevent accidental logging of the license-derived hash. */
  @Override
  public String toString() {
    return "AnalyticsExporterContext[clusterId=" + clusterId + ", partitionId=" + partitionId + "]";
  }

  static AnalyticsExporterContext create(
      final String licenseKey, final String clusterId, final int partitionId) {
    if (licenseKey == null || licenseKey.isBlank()) {
      throw new IllegalStateException("CAMUNDA_LICENSE_KEY is required for the analytics exporter");
    }
    return new AnalyticsExporterContext(computeFingerprint(licenseKey), clusterId, partitionId);
  }

  private static String computeFingerprint(final String licenseKey) {
    try {
      final var digest = MessageDigest.getInstance("SHA-256");
      final var hash = digest.digest(licenseKey.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
