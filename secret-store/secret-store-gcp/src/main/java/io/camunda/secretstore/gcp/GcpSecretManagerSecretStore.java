/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.ServiceOptions;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SecretStore} backed by GCP Secret Manager.
 *
 * <p>References are mapped to GCP secret ids by prepending the configured {@code pathPrefix} and
 * read from the {@code latest} version within the configured project. Authentication uses the GCP
 * Application Default Credentials chain (identity-based, no static credentials).
 *
 * <p>How a reference is actually looked up is delegated to one {@link GcpSecretResolver}, chosen
 * once at construction time: {@link OneByOneSecretResolver} by default (one GCP secret per
 * reference), or {@link ContainerSecretResolver} when a container secret id is set (every reference
 * is a JSON key inside one shared secret). This class itself only owns the GCP client and picks the
 * resolver; adding a new resolution mode means adding a new {@link GcpSecretResolver}
 * implementation, not touching this class or the existing ones.
 *
 * <p>Per-secret failures (missing secret, access denied, invalid reference) are returned as {@link
 * Failed} results. Store-wide failures (connectivity, throttling after retries, service errors) are
 * surfaced as {@link SecretStoreUnavailableException} so callers can retry or back off.
 *
 * <p>This class is thread-safe: {@link SecretManagerServiceClient} is thread-safe, {@link
 * GcpSecretResolver} implementations keep no mutable state between calls, and neither does this
 * class.
 */
public final class GcpSecretManagerSecretStore implements SecretStore {

  private static final Logger LOG = LoggerFactory.getLogger(GcpSecretManagerSecretStore.class);

  private final SecretManagerServiceClient client;
  private final GcpSecretResolver resolver;

  /**
   * Creates a store using an already-built client, with no JSON container. Package-private and
   * test-only: it skips the config resolution and startup probe done by {@link
   * #fromConfig(GcpSecretManagerStoreConfig)}, so production code must go through {@code
   * fromConfig} and only same-package tests can reach this constructor.
   */
  GcpSecretManagerSecretStore(
      final SecretManagerServiceClient client,
      final @Nullable String projectId,
      final @Nullable String pathPrefix) {
    this(client, projectId, pathPrefix, null);
  }

  /**
   * Creates a store using an already-built client, optionally treating every reference as a JSON
   * key inside the given container secret. Package-private and test-only: it skips the config
   * resolution and startup probe done by {@link #fromConfig(GcpSecretManagerStoreConfig)}, so
   * production code must go through {@code fromConfig} and only same-package tests can reach this
   * constructor.
   */
  GcpSecretManagerSecretStore(
      final SecretManagerServiceClient client,
      final @Nullable String projectId,
      final @Nullable String pathPrefix,
      final @Nullable String containerSecretId) {
    this(client, buildResolver(client, projectId, normalize(pathPrefix), containerSecretId));
  }

  private GcpSecretManagerSecretStore(
      final SecretManagerServiceClient client, final GcpSecretResolver resolver) {
    this.client = client;
    this.resolver = resolver;
  }

  /**
   * Builds a store from configuration, eagerly constructing the underlying client and probing
   * connectivity with a minimal GCP call. Two failure modes are distinguished:
   *
   * <ul>
   *   <li>Building the client fails (e.g. credentials cannot be loaded from the Application Default
   *       Credentials chain): this fails fast with a {@link SecretStoreUnavailableException}, since
   *       a client that cannot even be constructed can never resolve a secret.
   *   <li>The client builds but the startup probe fails (e.g. an unreachable endpoint or a bad
   *       project): this is best-effort — it is logged as a warning at startup but does not prevent
   *       the store from being built, so a transient GCP problem during boot cannot stop the whole
   *       application from starting. Such a misconfiguration then surfaces on the first real {@link
   *       #resolve} or {@link #list} call.
   * </ul>
   *
   * <p>A missing project id is treated differently: if the config omits it and no default project
   * can be resolved from the environment, this fails fast with a {@link
   * SecretStoreUnavailableException}, since that is a deterministic configuration error rather than
   * a transient connectivity problem.
   */
  public static GcpSecretManagerSecretStore fromConfig(final GcpSecretManagerStoreConfig config) {
    // Resolve the project id before opening the client: it does not depend on the client, and doing
    // it here keeps the throwing calls outside the try below so a failure cannot leak an
    // already-open client.
    final var projectId =
        config.projectId() != null ? config.projectId() : ServiceOptions.getDefaultProjectId();
    if (projectId == null || projectId.isBlank()) {
      // A missing project id is a deterministic configuration error, not a transient connectivity
      // problem, so fail fast with a clear message instead of deferring to a low-signal NPE from
      // ProjectName.of(null)/SecretVersionName.of(null, ...) on the first resolve/list.
      throw new SecretStoreUnavailableException(
          "GCP Secret Manager projectId is not configured and no default project could be resolved "
              + "from the environment; set projectId explicitly or provide GOOGLE_CLOUD_PROJECT / a "
              + "gcloud default project / a compute metadata server");
    }
    final SecretManagerServiceClient client;
    try {
      final var settings = SecretManagerServiceSettings.newBuilder();
      if (config.withoutAuthentication()) {
        // Emulator/testing only: no credentials and a plaintext gRPC channel to a local endpoint.
        final var endpoint =
            Objects.requireNonNull(
                config.endpoint(),
                "endpoint must be set when authentication is disabled (emulator testing only)");
        settings.setCredentialsProvider(NoCredentialsProvider.create());
        settings.setTransportChannelProvider(
            SecretManagerServiceSettings.defaultGrpcTransportProviderBuilder()
                .setEndpoint(endpoint)
                .setChannelConfigurator(ManagedChannelBuilder::usePlaintext)
                .build());
      } else if (config.endpoint() != null && !config.endpoint().isBlank()) {
        settings.setEndpoint(config.endpoint());
      }
      client = SecretManagerServiceClient.create(settings.build());
    } catch (final IOException | RuntimeException e) {
      throw new SecretStoreUnavailableException(
          "Failed to initialize GCP Secret Manager client: " + e.getMessage(), e);
    }
    final var resolver =
        buildResolver(
            client, projectId, normalize(config.pathPrefix()), config.containerSecretId());
    validateConnectivity(resolver);
    return new GcpSecretManagerSecretStore(client, resolver);
  }

  /**
   * Probes GCP connectivity/credentials at startup, delegating to the resolver so it uses the exact
   * API — and thus the minimal IAM policy — that mode resolves with. The probe is best-effort: on
   * failure it logs a warning and lets the store be built anyway, so a transient GCP problem during
   * boot cannot stop the application from starting. The misconfiguration then surfaces on the first
   * real {@link #resolve} or {@link #list} call. The client is kept open on failure because the
   * store retains it for those later calls.
   *
   * <p>Package-private so the warn-and-continue behaviour can be unit-tested with a stub resolver;
   * the per-mode probe choice is tested on the resolvers themselves.
   */
  static void validateConnectivity(final GcpSecretResolver resolver) {
    try {
      resolver.validateConnectivity();
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to validate GCP Secret Manager connectivity/credentials at startup; "
              + "the store will still be created and the error will surface on first use: {}",
          e.getMessage(),
          e);
    }
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    return resolver.resolve(names);
  }

  @Override
  public List<String> list() {
    return resolver.list();
  }

  @Override
  public int namesPerCall() {
    return resolver.namesPerCall();
  }

  @Override
  public void close() {
    client.close();
  }

  private static GcpSecretResolver buildResolver(
      final SecretManagerServiceClient client,
      final @Nullable String projectId,
      final String pathPrefix,
      final @Nullable String containerSecretId) {
    return containerSecretId == null || containerSecretId.isBlank()
        ? new OneByOneSecretResolver(client, projectId, pathPrefix)
        : new ContainerSecretResolver(client, projectId, pathPrefix, containerSecretId);
  }

  private static String normalize(final @Nullable String pathPrefix) {
    return pathPrefix == null || pathPrefix.isBlank() ? "" : pathPrefix;
  }
}
