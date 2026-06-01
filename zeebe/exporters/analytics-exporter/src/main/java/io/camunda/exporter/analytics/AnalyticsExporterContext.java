/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Context for an analytics exporter instance, derived from the broker context at configure time.
 * Bundles auth credentials (fingerprint, HMAC signer) and partition identity (clusterId,
 * partitionId).
 */
public final class AnalyticsExporterContext {

  static final String HEADER_FINGERPRINT = "x-camunda-fingerprint";
  static final String HEADER_CLUSTER_ID = "x-camunda-cluster-id";
  static final String HEADER_TIMESTAMP = "x-camunda-timestamp";
  static final String HEADER_SIGNATURE = "x-camunda-signature";

  private static final String SHA_256 = "SHA-256";
  private static final String HMAC_SHA_256 = "HmacSHA256";
  private static final String CANONICAL_FORMAT = "%s|%s|%s";
  private static final HexFormat HEX = HexFormat.of();

  private final String fingerprint;
  private final String clusterId;
  private final int partitionId;
  private final Mac signer;

  private AnalyticsExporterContext(
      final String fingerprint, final String clusterId, final int partitionId, final Mac signer) {
    this.fingerprint = fingerprint;
    this.clusterId = clusterId;
    this.partitionId = partitionId;
    this.signer = signer;
  }

  static AnalyticsExporterContext create(
      final String licenseKey, final String clusterId, final int partitionId) {
    if (licenseKey == null || licenseKey.isBlank()) {
      throw new IllegalArgumentException("licenseKey must not be null or blank");
    }
    try {
      final var keyBytes = licenseKey.getBytes(StandardCharsets.UTF_8);
      final var fingerprint = HEX.formatHex(MessageDigest.getInstance(SHA_256).digest(keyBytes));
      final var signer = Mac.getInstance(HMAC_SHA_256);
      signer.init(new SecretKeySpec(keyBytes, HMAC_SHA_256));
      return new AnalyticsExporterContext(fingerprint, clusterId, partitionId, signer);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(
          "JVM does not support required crypto algorithms (SHA-256 or HmacSHA256)", e);
    } catch (final InvalidKeyException e) {
      throw new IllegalStateException("License key is not valid for HMAC signing", e);
    }
  }

  String fingerprint() {
    return fingerprint;
  }

  String clusterId() {
    return clusterId;
  }

  int partitionId() {
    return partitionId;
  }

  /**
   * Computes per-request HMAC signature headers (timestamp + signature). Called by the OTel SDK's
   * export thread via {@code setHeaders(Supplier)} — synchronized because {@link Mac#doFinal} is
   * not thread-safe.
   */
  Map<String, String> computeSignatureHeaders() {
    final var timestamp = String.valueOf(Instant.now().getEpochSecond());
    final var canonical = CANONICAL_FORMAT.formatted(fingerprint, clusterId, timestamp);
    final byte[] sig;
    synchronized (signer) {
      sig = signer.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
    }
    return Map.of(HEADER_TIMESTAMP, timestamp, HEADER_SIGNATURE, HEX.formatHex(sig));
  }

  /** Redact fingerprint and signing key to prevent accidental logging. */
  @Override
  public String toString() {
    return "AnalyticsExporterContext[clusterId=" + clusterId + ", partitionId=" + partitionId + "]";
  }
}
