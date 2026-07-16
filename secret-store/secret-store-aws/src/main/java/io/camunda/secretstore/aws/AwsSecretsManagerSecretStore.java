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
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DecryptionFailureException;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.InvalidParameterException;
import software.amazon.awssdk.services.secretsmanager.model.InvalidRequestException;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
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
 * <p>This class is thread-safe: {@link SecretsManagerClient} is thread-safe and no mutable state is
 * kept between calls.
 */
public final class AwsSecretsManagerSecretStore
    implements SecretStore<AwsSecretsManagerSecretReference> {

  private static final Logger LOG = LoggerFactory.getLogger(AwsSecretsManagerSecretStore.class);

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
    return new AwsSecretsManagerSecretStore(client, config.pathPrefix());
  }

  @Override
  public Map<AwsSecretsManagerSecretReference, SecretResolutionResult> resolve(
      final Set<AwsSecretsManagerSecretReference> refs) {
    if (refs.isEmpty()) {
      return Map.of();
    }
    LOG.debug("Resolving {} secret refs from AWS Secrets Manager", refs.size());
    final Map<AwsSecretsManagerSecretReference, SecretResolutionResult> results =
        new LinkedHashMap<>(refs.size());
    for (final var ref : refs) {
      results.put(ref, resolveSingle(ref));
    }
    return results;
  }

  private SecretResolutionResult resolveSingle(final AwsSecretsManagerSecretReference ref) {
    final var secretId = secretId(ref.name());
    try {
      final var response =
          client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build());
      final var value = response.secretString();
      if (value == null) {
        // Binary secrets are not supported for string resolution.
        return new Failed(
            INVALID_REF, "Secret '" + secretId + "' has no string value (binary secret)", null);
      }
      return new Resolved(value);
    } catch (final ResourceNotFoundException e) {
      return new Failed(NOT_FOUND, "Secret not found: " + secretId, e);
    } catch (final InvalidParameterException | InvalidRequestException e) {
      return new Failed(INVALID_REF, "Invalid secret reference: " + secretId, e);
    } catch (final DecryptionFailureException e) {
      return new Failed(ACCESS_DENIED, "Cannot decrypt secret: " + secretId, e);
    } catch (final SecretsManagerException e) {
      if (isAccessDenied(e)) {
        return new Failed(ACCESS_DENIED, "Access denied for secret: " + secretId, e);
      }
      throw storeUnavailable(e);
    } catch (final SdkClientException e) {
      throw storeUnavailable(e);
    }
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
