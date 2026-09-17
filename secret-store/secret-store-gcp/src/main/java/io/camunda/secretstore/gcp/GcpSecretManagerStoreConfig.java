/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for a {@link GcpSecretManagerSecretStore}.
 *
 * <p>Authentication is always identity-based: the GCP Application Default Credentials chain is used
 * (workload identity, service-account key from {@code GOOGLE_APPLICATION_CREDENTIALS}, metadata
 * server). No static credentials are accepted here by design.
 *
 * @param projectId the GCP project id owning the secrets, or {@code null} to let the client resolve
 *     it from the environment (the {@code GOOGLE_CLOUD_PROJECT} variable, {@code gcloud} config, or
 *     the compute metadata server via the Application Default Credentials chain)
 * @param pathPrefix optional prefix prepended to every reference name to form the GCP secret id
 *     (e.g. {@code camunda-}); {@code null} or blank means references map to bare secret ids
 * @param endpoint optional endpoint override, primarily for testing against an emulator; {@code
 *     null} uses the default Secret Manager endpoint
 * @param containerSecretId opt-in: when set, every reference is treated as a JSON key inside this
 *     one named secret instead of its own GCP secret; {@code null} keeps the default one-secret
 *     per-reference behavior
 * @param withoutAuthentication testing only: when {@code true}, the client connects to {@code
 *     endpoint} over a plaintext channel with no credentials, so an integration test can point it
 *     at a local Secret Manager emulator. Requires {@code endpoint} to be set and must never be
 *     enabled against real GCP; production always leaves this {@code false} and authenticates via
 *     the Application Default Credentials chain.
 * @param callTimeout upper bound on one {@code resolve}/{@code list} call to GCP, retries included.
 *     Must be positive. gax's defaults let an unresponsive endpoint hold the caller far longer than
 *     that; the background resolution calls this store from an IO-bound actor thread shared with
 *     every partition's exporter, so an unbounded call there stalls exporting broker-wide
 *     (camunda/camunda#62869)
 */
public record GcpSecretManagerStoreConfig(
    @Nullable String projectId,
    @Nullable String pathPrefix,
    @Nullable String endpoint,
    @Nullable String containerSecretId,
    boolean withoutAuthentication,
    Duration callTimeout) {

  /**
   * Default upper bound on one call to GCP, retries included. Matches the AWS store's default, and
   * is chosen so that a store that has stopped answering is reported unavailable within a few
   * resolution cycles rather than after minutes: that is what lets the scheduler's retry ladder and
   * its cooldown skip engage.
   */
  public static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(5);

  public GcpSecretManagerStoreConfig {
    if (projectId != null && projectId.isBlank()) {
      throw new IllegalArgumentException("projectId must not be blank when set");
    }
    if (containerSecretId != null && containerSecretId.isBlank()) {
      throw new IllegalArgumentException("containerSecretId must not be blank, but was empty");
    }
    if (withoutAuthentication && (endpoint == null || endpoint.isBlank())) {
      throw new IllegalArgumentException(
          "endpoint must be set when authentication is disabled (emulator testing only)");
    }
    if (callTimeout == null || !callTimeout.isPositive()) {
      throw new IllegalArgumentException("callTimeout must be positive, but was " + callTimeout);
    }
  }

  /**
   * Creates a config with the default call timeout. Every caller that does not tune it goes through
   * here, so a store is bounded whether or not an operator configured it.
   */
  public GcpSecretManagerStoreConfig(
      final @Nullable String projectId,
      final @Nullable String pathPrefix,
      final @Nullable String endpoint,
      final @Nullable String containerSecretId,
      final boolean withoutAuthentication) {
    this(
        projectId,
        pathPrefix,
        endpoint,
        containerSecretId,
        withoutAuthentication,
        DEFAULT_CALL_TIMEOUT);
  }

  /**
   * Authenticated config (production default): connects to the given endpoint (or the default one
   * when {@code null}) using the Application Default Credentials chain.
   */
  public GcpSecretManagerStoreConfig(
      final @Nullable String projectId,
      final @Nullable String pathPrefix,
      final @Nullable String endpoint,
      final @Nullable String containerSecretId) {
    this(projectId, pathPrefix, endpoint, containerSecretId, false);
  }

  /**
   * Creates a config with only a project id and path prefix; endpoint and container default to
   * {@code null} (one GCP secret per reference against the default endpoint).
   */
  public static GcpSecretManagerStoreConfig of(
      final @Nullable String projectId, final @Nullable String pathPrefix) {
    return new GcpSecretManagerStoreConfig(projectId, pathPrefix, null, null);
  }
}
