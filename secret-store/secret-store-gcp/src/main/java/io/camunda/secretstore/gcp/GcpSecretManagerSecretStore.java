/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.secretmanager.v1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1.ProjectName;
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
import java.util.Set;
import org.jspecify.annotations.Nullable;

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

  private final SecretManagerServiceClient client;
  private final GcpSecretResolver resolver;

  /**
   * Creates a store using an already-built client, with no JSON container. Primarily for testing;
   * production code should use {@link #fromConfig(GcpSecretManagerStoreConfig)}.
   */
  public GcpSecretManagerSecretStore(
      final SecretManagerServiceClient client,
      final String projectId,
      final @Nullable String pathPrefix) {
    this(client, projectId, pathPrefix, null);
  }

  /**
   * Creates a store using an already-built client, optionally treating every reference as a JSON
   * key inside the given container secret. Primarily for testing; production code should use {@link
   * #fromConfig(GcpSecretManagerStoreConfig)}.
   */
  public GcpSecretManagerSecretStore(
      final SecretManagerServiceClient client,
      final String projectId,
      final @Nullable String pathPrefix,
      final @Nullable String containerSecretId) {
    this.client = client;
    final var prefix = normalize(pathPrefix);
    resolver =
        containerSecretId == null || containerSecretId.isBlank()
            ? new OneByOneSecretResolver(client, projectId, prefix)
            : new ContainerSecretResolver(client, projectId, prefix, containerSecretId);
  }

  /**
   * Builds a store from configuration, eagerly constructing the underlying client and validating
   * connectivity/credentials with a minimal GCP call. Failing fast here means a bad project,
   * unreachable endpoint, or invalid/missing credentials surfaces immediately at startup instead of
   * being deferred to the first real {@link #resolve} or {@link #list} call.
   */
  public static GcpSecretManagerSecretStore fromConfig(final GcpSecretManagerStoreConfig config) {
    final SecretManagerServiceClient client;
    try {
      final var settings = SecretManagerServiceSettings.newBuilder();
      if (config.withoutAuthentication()) {
        // Emulator/testing only: no credentials and a plaintext gRPC channel to a local endpoint.
        settings.setCredentialsProvider(NoCredentialsProvider.create());
        settings.setTransportChannelProvider(
            SecretManagerServiceSettings.defaultGrpcTransportProviderBuilder()
                .setEndpoint(config.endpoint())
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
    validateConnectivity(client, config.projectId());
    return new GcpSecretManagerSecretStore(
        client, config.projectId(), config.pathPrefix(), config.containerSecretId());
  }

  /**
   * Proves the client can authenticate and reach GCP Secret Manager with a minimal call. Closes the
   * client and fails fast on any error, rather than leaving misconfiguration (bad
   * project/credentials/endpoint/network) to surface on the first real {@link #resolve} or {@link
   * #list} call.
   */
  static void validateConnectivity(
      final SecretManagerServiceClient client, final String projectId) {
    try {
      // page size 1: we only need the RPC to round-trip, not the contents
      client
          .listSecrets(
              ListSecretsRequest.newBuilder()
                  .setParent(ProjectName.of(projectId).toString())
                  .setPageSize(1)
                  .build())
          .getPage()
          .getResponse();
    } catch (final RuntimeException e) {
      client.close();
      throw new SecretStoreUnavailableException(
          "Failed to validate GCP Secret Manager connectivity/credentials at startup: "
              + e.getMessage(),
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
  public void close() {
    client.close();
  }

  private static String normalize(final @Nullable String pathPrefix) {
    return pathPrefix == null || pathPrefix.isBlank() ? "" : pathPrefix;
  }
}
