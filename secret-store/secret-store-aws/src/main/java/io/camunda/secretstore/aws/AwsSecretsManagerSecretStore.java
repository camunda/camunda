/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * A {@link SecretStore} backed by AWS Secrets Manager.
 *
 * <p>References are mapped to AWS secret ids by prepending the configured {@code pathPrefix}.
 * Values are read from the {@code AWSCURRENT} version stage (the SDK default). Authentication uses
 * the AWS SDK default credentials provider chain (identity-based, no static credentials).
 *
 * <p>How a reference is actually looked up is delegated to one {@link AwsSecretResolver}, chosen
 * once at construction time: {@link OneByOneSecretResolver} by default (one AWS secret per
 * reference), or {@link ContainerSecretResolver} when {@link
 * AwsSecretsManagerStoreConfig#containerSecretId} is set (every reference is a JSON key inside one
 * shared secret). This class itself only owns the AWS client and picks the resolver; adding a new
 * resolution mode means adding a new {@link AwsSecretResolver} implementation, not touching this
 * class or the existing ones.
 *
 * <p>Per-secret failures (missing secret, access denied, invalid reference) are returned as {@link
 * Failed} results. Store-wide failures (connectivity, throttling after retries, service errors) are
 * surfaced as {@link SecretStoreUnavailableException} so callers can retry or back off.
 *
 * <p>This class is thread-safe: {@link SecretsManagerClient} is thread-safe and no mutable state is
 * kept between calls.
 */
public final class AwsSecretsManagerSecretStore
    implements SecretStore<AwsSecretsManagerSecretReference> {

  private final SecretsManagerClient client;
  private final AwsSecretResolver resolver;

  /**
   * Creates a store using an already-built client, with no JSON container. Primarily for testing;
   * production code should use {@link #fromConfig(AwsSecretsManagerStoreConfig)}.
   */
  public AwsSecretsManagerSecretStore(
      final SecretsManagerClient client, final @Nullable String pathPrefix) {
    this(client, pathPrefix, null);
  }

  /**
   * Creates a store using an already-built client, optionally treating every reference as a JSON
   * key inside the given container secret. Primarily for testing; production code should use {@link
   * #fromConfig(AwsSecretsManagerStoreConfig)}.
   */
  public AwsSecretsManagerSecretStore(
      final SecretsManagerClient client,
      final @Nullable String pathPrefix,
      final @Nullable String containerSecretId) {
    this.client = client;
    final var prefix = pathPrefix == null ? "" : pathPrefix;
    resolver =
        containerSecretId == null || containerSecretId.isBlank()
            ? new OneByOneSecretResolver(client, prefix)
            : new ContainerSecretResolver(client, prefix, containerSecretId);
  }

  /**
   * Builds a store from configuration, eagerly constructing the underlying client. Building the
   * client validates the region and endpoint at startup, failing fast on misconfiguration.
   */
  public static AwsSecretsManagerSecretStore fromConfig(final AwsSecretsManagerStoreConfig config) {
    final var builder =
        SecretsManagerClient.builder()
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .retryPolicy(RetryPolicy.builder().numRetries(config.maxRetries()).build())
                    .build());
    if (config.region() != null && !config.region().isBlank()) {
      builder.region(Region.of(config.region()));
    }
    if (config.endpoint() != null) {
      builder.endpointOverride(config.endpoint());
    }
    final SecretsManagerClient client;
    try {
      client = builder.build();
    } catch (final RuntimeException e) {
      throw new SecretStoreUnavailableException(
          "Failed to initialize AWS Secrets Manager client: " + e.getMessage(), e);
    }
    return new AwsSecretsManagerSecretStore(
        client, config.pathPrefix(), config.containerSecretId());
  }

  @Override
  public Map<AwsSecretsManagerSecretReference, SecretResolutionResult> resolve(
      final Set<AwsSecretsManagerSecretReference> refs) {
    return refs.isEmpty() ? Map.of() : resolver.resolve(refs);
  }

  @Override
  public Collection<AwsSecretsManagerSecretReference> list() {
    return resolver.list();
  }

  @Override
  public void close() {
    client.close();
  }
}
