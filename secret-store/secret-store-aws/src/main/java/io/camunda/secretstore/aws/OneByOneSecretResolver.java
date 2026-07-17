/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.classify;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.storeUnavailable;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/** Default resolution mode: one AWS secret per reference (one {@code GetSecretValue} call each). */
final class OneByOneSecretResolver implements AwsSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(OneByOneSecretResolver.class);

  private final SecretsManagerClient client;
  private final String pathPrefix;

  OneByOneSecretResolver(final SecretsManagerClient client, final String pathPrefix) {
    this.client = client;
    this.pathPrefix = pathPrefix;
  }

  @Override
  public Map<AwsSecretsManagerSecretReference, SecretResolutionResult> resolve(
      final Set<AwsSecretsManagerSecretReference> refs) {
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
    } catch (final SecretsManagerException e) {
      final var code = classify(e);
      return new Failed(code, messageFor(code, secretId), e);
    } catch (final SdkClientException e) {
      throw storeUnavailable(e);
    }
  }

  private static String messageFor(final SecretErrorCode code, final String secretId) {
    return switch (code) {
      case NOT_FOUND -> "Secret not found: " + secretId;
      case INVALID_REF -> "Invalid secret reference: " + secretId;
      case ACCESS_DENIED -> "Access denied for secret: " + secretId;
    };
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

  private String secretId(final String name) {
    return pathPrefix + name;
  }
}
