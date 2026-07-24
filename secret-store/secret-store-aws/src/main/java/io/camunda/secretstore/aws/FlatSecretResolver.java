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
import static io.camunda.secretstore.SecretErrorCode.UNREADABLE;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.classify;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.classifyErrorCode;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.isAccessDenied;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.messageFor;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.storeUnavailable;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.APIErrorType;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * Default resolution mode: each reference name maps 1:1 to an AWS secret id ({@code pathPrefix +
 * name}). Issues one {@code GetSecretValue} call per reference by default; switches to batched
 * {@code BatchGetSecretValue} calls (up to {@code batchSize} ids each) when {@code batchEnabled}.
 */
final class FlatSecretResolver implements AwsSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(FlatSecretResolver.class);

  private final SecretsManagerClient client;
  private final String pathPrefix;
  private final boolean batchEnabled;
  private final int batchSize;

  FlatSecretResolver(
      final SecretsManagerClient client,
      final String pathPrefix,
      final boolean batchEnabled,
      final int batchSize) {
    this.client = client;
    this.pathPrefix = pathPrefix;
    this.batchEnabled = batchEnabled;
    this.batchSize = batchSize;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug("Resolving {} secret refs from AWS Secrets Manager", names.size());
    final Map<String, String> secretIdToName = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      secretIdToName.put(secretId(name), name);
    }
    return batchEnabled ? resolveBatched(secretIdToName) : resolveOneByOne(secretIdToName);
  }

  private Map<String, SecretResolutionResult> resolveOneByOne(
      final Map<String, String> secretIdToName) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(secretIdToName.size());
    for (final var entry : secretIdToName.entrySet()) {
      results.put(entry.getValue(), resolveSingle(entry.getKey()));
    }
    return results;
  }

  private SecretResolutionResult resolveSingle(final String secretId) {
    try {
      final var response =
          client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build());
      return toResolutionResult(secretId, response.secretString());
    } catch (final SecretsManagerException e) {
      final var code = classify(e);
      return new Failed(code, messageFor(code, "secret", secretId), e);
    } catch (final SdkClientException e) {
      throw storeUnavailable(e);
    }
  }

  private Map<String, SecretResolutionResult> resolveBatched(
      final Map<String, String> secretIdToName) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(secretIdToName.size());
    for (final var batch : partition(secretIdToName.keySet(), batchSize)) {
      resolveBatch(batch, secretIdToName, results);
    }
    return results;
  }

  /**
   * Resolves one {@code BatchGetSecretValue} call and merges its outcome into {@code results}.
   *
   * <p>AWS reports failures for this API in two distinct shapes, handled differently here:
   *
   * <ul>
   *   <li><b>Per-secret failure</b>: the call succeeds and returns a normal response; a secret that
   *       couldn't be read (not found, access denied, etc.) shows up as an entry in {@code
   *       response.errors()} alongside other secrets resolved fine in {@code
   *       response.secretValues()}. Handled by {@link #mapBatchError}, scoped to that one secret.
   *   <li><b>Whole-call failure</b>: {@code batchGetSecretValue} itself throws — no response body
   *       exists at all (e.g. the caller lacks the {@code BatchGetSecretValue} action entirely).
   *       There is no per-secret information to read in this case, so every id in {@code secretIds}
   *       (this batch only, not other batches in the same {@link #resolve} call) is marked {@code
   *       ACCESS_DENIED} or the call throws {@link
   *       io.camunda.secretstore.SecretStoreUnavailableException}, below.
   * </ul>
   */
  private void resolveBatch(
      final List<String> secretIds,
      final Map<String, String> secretIdToName,
      final Map<String, SecretResolutionResult> results) {
    try {
      final var response =
          client.batchGetSecretValue(
              BatchGetSecretValueRequest.builder().secretIdList(secretIds).build());
      final var pending = new LinkedHashSet<>(secretIds);
      for (final var entry : response.secretValues()) {
        final var name = secretIdToName.get(entry.name());
        if (name == null) {
          continue;
        }
        pending.remove(entry.name());
        results.put(name, toResolutionResult(entry.name(), entry.secretString()));
      }
      for (final var error : response.errors()) {
        final var name = secretIdToName.get(error.secretId());
        if (name == null) {
          continue;
        }
        pending.remove(error.secretId());
        results.put(name, mapBatchError(error));
      }
      // defensive: guarantee a result for every name even if AWS omits one from both lists
      for (final var secretId : pending) {
        results.put(
            secretIdToName.get(secretId),
            new Failed(NOT_FOUND, "No result returned for secret: " + secretId, null));
      }
    } catch (final SecretsManagerException e) {
      if (isAccessDenied(e)) {
        for (final var secretId : secretIds) {
          results.put(
              secretIdToName.get(secretId),
              new Failed(
                  ACCESS_DENIED,
                  "Secret '"
                      + secretId
                      + "': the entire batch request was denied ("
                      + e.getMessage()
                      + ")",
                  e));
        }
        return;
      }
      throw storeUnavailable(e);
    } catch (final SdkClientException e) {
      throw storeUnavailable(e);
    }
  }

  private static SecretResolutionResult mapBatchError(final APIErrorType error) {
    final var classified = classifyErrorCode(error.errorCode());
    if (classified != null) {
      return new Failed(classified, "Secret '" + error.secretId() + "': " + error.message(), null);
    }
    // unrecognized AWS error code: not a known per-secret condition, so don't mislabel it
    // INVALID_REF — surface as UNREADABLE without aborting the rest of the batch
    return new Failed(
        UNREADABLE,
        messageFor(UNREADABLE, "secret", error.secretId())
            + " (unrecognized AWS error code '"
            + error.errorCode()
            + "': "
            + error.message()
            + ")",
        null);
  }

  private static SecretResolutionResult toResolutionResult(
      final String secretId, final @Nullable String secretString) {
    if (secretString == null) {
      // AWS returns exactly one of secretString/secretBinary per secret; null here means the
      // value lives in secretBinary instead, which this store doesn't support resolving.
      return new Failed(
          INVALID_REF, "Secret '" + secretId + "' has no string value (binary secret)", null);
    }
    return new Resolved(secretString);
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
  public List<String> list() {
    LOG.debug("Listing secrets from AWS Secrets Manager with prefix '{}'", pathPrefix);
    final var names = new ArrayList<String>();
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
              names.add(logicalName);
            }
          }
        }
        nextToken = response.nextToken();
      } while (nextToken != null);
      return names;
    } catch (final SecretsManagerException | SdkClientException e) {
      throw storeUnavailable(e);
    }
  }

  private String secretId(final String name) {
    return pathPrefix + name;
  }
}
