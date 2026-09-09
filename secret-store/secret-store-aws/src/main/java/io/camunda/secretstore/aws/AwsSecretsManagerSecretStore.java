/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.DefaultRetryStrategy;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * A {@link SecretStore} backed by AWS Secrets Manager.
 *
 * <p>References are mapped to AWS secret ids by prepending the configured {@code pathPrefix}.
 * Values are read from the {@code AWSCURRENT} version stage (the SDK default). Authentication uses
 * the AWS SDK default credentials provider chain.
 *
 * <p>How a reference is actually looked up is delegated to one {@link AwsSecretResolver}, chosen
 * once at construction time: {@link FlatSecretResolver} by default (one AWS secret per reference,
 * optionally batched via {@code BatchGetSecretValue}), or {@link ContainerSecretResolver} when a
 * container secret id is set (every reference is a JSON key inside one shared secret). This class
 * itself only owns the AWS client and picks the resolver; adding a new resolution mode means adding
 * a new {@link AwsSecretResolver} implementation, not touching this class or the existing ones.
 *
 * <p>Per-secret failures (missing secret, access denied, invalid reference) are returned as {@link
 * SecretResolutionResult.Failed} results. Store-wide failures (connectivity, throttling after
 * retries, service errors) are surfaced as {@link SecretStoreUnavailableException} so callers can
 * retry or back off.
 *
 * <p>This class is thread-safe: {@link SecretsManagerClient} is thread-safe, {@link
 * AwsSecretResolver} implementations keep no mutable state between calls, and neither does this
 * class.
 */
public final class AwsSecretsManagerSecretStore implements SecretStore {

  private static final Logger LOG = LoggerFactory.getLogger(AwsSecretsManagerSecretStore.class);

  private final SecretsManagerClient client;
  private final AwsSecretResolver resolver;

  /**
   * Creates a store using an already-built client, with batching disabled and no container secret.
   * Primarily for testing; production code should use {@link
   * #fromConfig(AwsSecretsManagerStoreConfig)}.
   */
  public AwsSecretsManagerSecretStore(
      final SecretsManagerClient client, final @Nullable String pathPrefix) {
    this(client, pathPrefix, false, AwsSecretsManagerStoreConfig.DEFAULT_BATCH_SIZE);
  }

  /**
   * Creates a store using an already-built client, with explicit batching control. Primarily for
   * testing; production code should use {@link #fromConfig(AwsSecretsManagerStoreConfig)}.
   */
  public AwsSecretsManagerSecretStore(
      final SecretsManagerClient client,
      final @Nullable String pathPrefix,
      final boolean batchEnabled,
      final int batchSize) {
    if (batchSize < 1 || batchSize > AwsSecretsManagerStoreConfig.MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "batchSize must be between 1 and "
              + AwsSecretsManagerStoreConfig.MAX_BATCH_SIZE
              + ", but was "
              + batchSize);
    }
    this.client = client;
    resolver = new FlatSecretResolver(client, normalize(pathPrefix), batchEnabled, batchSize);
  }

  private AwsSecretsManagerSecretStore(
      final SecretsManagerClient client, final AwsSecretResolver resolver) {
    this.client = client;
    this.resolver = resolver;
  }

  /**
   * Builds a store from configuration, eagerly constructing the underlying client and probing
   * connectivity/credentials with a minimal AWS call. The probe is best-effort: a bad region,
   * unreachable endpoint, or invalid/missing credentials is logged as a warning at startup but does
   * not prevent the store from being built, so a transient AWS problem during boot cannot stop the
   * whole application from starting. Such a misconfiguration then surfaces on the first real {@link
   * #resolve} or {@link #list} call.
   */
  public static AwsSecretsManagerSecretStore fromConfig(final AwsSecretsManagerStoreConfig config) {
    final var builder =
        SecretsManagerClient.builder()
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .retryStrategy(
                        DefaultRetryStrategy.standardStrategyBuilder()
                            // maxAttempts counts the initial try, maxRetries doesn't
                            .maxAttempts(config.maxRetries() + 1)
                            .build())
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
    final var pathPrefix = normalize(config.pathPrefix());
    final var containerSecretId = config.containerSecretId();
    final AwsSecretResolver resolver =
        containerSecretId != null
            ? new ContainerSecretResolver(client, pathPrefix, containerSecretId)
            : new FlatSecretResolver(client, pathPrefix, config.batchEnabled(), config.batchSize());
    validateConnectivity(resolver);
    return new AwsSecretsManagerSecretStore(client, resolver);
  }

  /**
   * Probes AWS connectivity/credentials at startup, delegating to the resolver so it uses the exact
   * API — and thus the minimal IAM policy — that mode resolves with. The probe is best-effort: on
   * failure it logs a warning and lets the store be built anyway, so a transient AWS problem during
   * boot cannot stop the application from starting. The misconfiguration then surfaces on the first
   * real {@link #resolve} or {@link #list} call. The client is kept open on failure because the
   * store retains it for those later calls.
   *
   * <p>Package-private so the warn-and-continue behaviour can be unit-tested with a stub resolver;
   * the per-mode probe choice is tested on the resolvers themselves.
   */
  static void validateConnectivity(final AwsSecretResolver resolver) {
    try {
      resolver.validateConnectivity();
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to validate AWS Secrets Manager connectivity/credentials at startup; "
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

  private static String normalize(final @Nullable String pathPrefix) {
    return pathPrefix == null || pathPrefix.isBlank() ? "" : pathPrefix;
  }
}
