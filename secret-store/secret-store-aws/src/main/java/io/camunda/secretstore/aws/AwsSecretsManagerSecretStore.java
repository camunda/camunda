/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static io.camunda.secretstore.SecretErrorCode.ACCESS_DENIED;
import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.DefaultRetryStrategy;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.APIErrorType;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * A {@link SecretStore} backed by AWS Secrets Manager.
 *
 * <p>References are mapped to AWS secret ids by prepending the configured {@code pathPrefix}.
 * Values are read from the {@code AWSCURRENT} version stage (the SDK default). Authentication uses
 * the AWS SDK default credentials provider chain (identity-based, no static credentials).
 *
 * <p>Per-secret failures (missing secret, access denied, invalid reference) are returned as {@link
 * Failed} results. Store-wide failures (connectivity, throttling after retries, service errors) are
 * surfaced as {@link SecretStoreUnavailableException} so callers can retry or back off.
 *
 * <p>{@link #resolve} batches requests via {@code BatchGetSecretValue} ({@value #BATCH_SIZE} secret
 * ids per call, AWS's per-call limit) instead of one API call per reference.
 *
 * <p>This class is thread-safe: {@link SecretsManagerClient} is thread-safe and no mutable state is
 * kept between calls.
 */
public final class AwsSecretsManagerSecretStore
    implements SecretStore<AwsSecretsManagerSecretReference> {

  private static final Logger LOG = LoggerFactory.getLogger(AwsSecretsManagerSecretStore.class);

  /** Maximum number of secret ids AWS accepts per {@code BatchGetSecretValue} call. */
  private static final int BATCH_SIZE = 20;

  private final SecretsManagerClient client;
  private final String pathPrefix;

  /**
   * Creates a store using an already-built client. Primarily for testing; production code should
   * use {@link #fromConfig(AwsSecretsManagerStoreConfig)}.
   */
  public AwsSecretsManagerSecretStore(
      final SecretsManagerClient client,
      final @org.jspecify.annotations.Nullable String pathPrefix) {
    this.client = client;
    this.pathPrefix = pathPrefix == null ? "" : pathPrefix;
  }

  /**
   * Builds a store from configuration, eagerly constructing the underlying client and validating
   * connectivity/credentials with a minimal AWS call. Failing fast here means a bad region,
   * unreachable endpoint, or invalid/missing credentials surfaces immediately at startup instead of
   * being deferred to the first real {@link #resolve} or {@link #list} call.
   */
  public static AwsSecretsManagerSecretStore fromConfig(final AwsSecretsManagerStoreConfig config) {
    final var builder =
        SecretsManagerClient.builder()
            .credentialsProvider(DefaultCredentialsProvider.create())
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
    validateConnectivity(client);
    return new AwsSecretsManagerSecretStore(client, config.pathPrefix());
  }

  /**
   * Proves the client can authenticate and reach AWS Secrets Manager with a minimal call. Closes
   * the client and fails fast on any error, rather than leaving misconfiguration (bad
   * region/credentials/network) to surface on the first real {@link #resolve} or {@link #list}.
   */
  private static void validateConnectivity(final SecretsManagerClient client) {
    try {
      client.listSecrets(ListSecretsRequest.builder().maxResults(1).build());
    } catch (final RuntimeException e) {
      client.close();
      throw new SecretStoreUnavailableException(
          "Failed to validate AWS Secrets Manager connectivity/credentials at startup: "
              + e.getMessage(),
          e);
    }
  }

  @Override
  public Map<AwsSecretsManagerSecretReference, SecretResolutionResult> resolve(
      final Set<AwsSecretsManagerSecretReference> refs) {
    if (refs.isEmpty()) {
      return Map.of();
    }
    LOG.debug("Resolving {} secret refs from AWS Secrets Manager", refs.size());
    final Map<String, AwsSecretsManagerSecretReference> refsBySecretId =
        new LinkedHashMap<>(refs.size());
    for (final var ref : refs) {
      refsBySecretId.put(secretId(ref.name()), ref);
    }
    final Map<AwsSecretsManagerSecretReference, SecretResolutionResult> results =
        new LinkedHashMap<>(refs.size());
    for (final var batch : partition(refsBySecretId.keySet(), BATCH_SIZE)) {
      resolveBatch(batch, refsBySecretId, results);
    }
    return results;
  }

  private void resolveBatch(
      final List<String> secretIds,
      final Map<String, AwsSecretsManagerSecretReference> refsBySecretId,
      final Map<AwsSecretsManagerSecretReference, SecretResolutionResult> results) {
    try {
      final var response =
          client.batchGetSecretValue(
              BatchGetSecretValueRequest.builder().secretIdList(secretIds).build());
      final var pending = new LinkedHashSet<>(secretIds);
      for (final var entry : response.secretValues()) {
        final var ref = refsBySecretId.get(entry.name());
        if (ref == null) {
          continue;
        }
        pending.remove(entry.name());
        final var value = entry.secretString();
        results.put(
            ref,
            value == null
                ? new Failed(
                    INVALID_REF,
                    "Secret '" + entry.name() + "' has no string value (binary secret)",
                    null)
                : new Resolved(value));
      }
      for (final var error : response.errors()) {
        final var ref = refsBySecretId.get(error.secretId());
        if (ref == null) {
          continue;
        }
        pending.remove(error.secretId());
        results.put(ref, mapBatchError(error));
      }
      // defensive: guarantee a result for every ref even if AWS omits one from both lists
      for (final var secretId : pending) {
        results.put(
            refsBySecretId.get(secretId),
            new Failed(NOT_FOUND, "No result returned for secret: " + secretId, null));
      }
    } catch (final SecretsManagerException e) {
      if (isAccessDenied(e)) {
        for (final var secretId : secretIds) {
          results.put(
              refsBySecretId.get(secretId),
              new Failed(ACCESS_DENIED, "Access denied for secret: " + secretId, e));
        }
        return;
      }
      throw storeUnavailable(e);
    } catch (final SdkClientException e) {
      throw storeUnavailable(e);
    }
  }

  private static SecretResolutionResult mapBatchError(final APIErrorType error) {
    final var message = "Secret '" + error.secretId() + "': " + error.message();
    return switch (error.errorCode()) {
      case "ResourceNotFoundException" -> new Failed(NOT_FOUND, message, null);
      case "DecryptionFailure", "AccessDeniedException" -> new Failed(ACCESS_DENIED, message, null);
      default -> new Failed(INVALID_REF, message, null);
    };
  }

  private static List<List<String>> partition(final Collection<String> items, final int size) {
    final var all = new ArrayList<>(items);
    final List<List<String>> batches = new ArrayList<>();
    for (int i = 0; i < all.size(); i += size) {
      batches.add(all.subList(i, Math.min(i + size, all.size())));
    }
    return batches;
  }

  @Override
  public Collection<AwsSecretsManagerSecretReference> list() {
    LOG.debug("Listing secrets from AWS Secrets Manager with prefix '{}'", pathPrefix);
    final var refs = new ArrayList<AwsSecretsManagerSecretReference>();
    String nextToken = null;
    try {
      do {
        final var requestBuilder = ListSecretsRequest.builder().maxResults(100);
        if (nextToken != null) {
          requestBuilder.nextToken(nextToken);
        }
        final var response = client.listSecrets(requestBuilder.build());
        for (final var entry : response.secretList()) {
          final var name = entry.name();
          if (name != null && name.startsWith(pathPrefix)) {
            final var logicalName = name.substring(pathPrefix.length());
            if (!logicalName.isBlank()) {
              refs.add(new AwsSecretsManagerSecretReference(logicalName));
            }
          }
        }
        nextToken = response.nextToken();
      } while (nextToken != null);
      return refs;
    } catch (final SecretsManagerException | SdkClientException e) {
      throw storeUnavailable(e);
    }
  }

  @Override
  public void close() {
    client.close();
  }

  private String secretId(final String name) {
    return pathPrefix + name;
  }

  private static boolean isAccessDenied(final SecretsManagerException e) {
    final var details = e.awsErrorDetails();
    if (details != null && details.errorCode() != null) {
      return details.errorCode().contains("AccessDenied");
    }
    return e.statusCode() == 403;
  }

  private static SecretStoreUnavailableException storeUnavailable(final Exception e) {
    return new SecretStoreUnavailableException(
        "AWS Secrets Manager is unavailable: " + e.getMessage(), e);
  }
}
