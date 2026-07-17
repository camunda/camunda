/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.classify;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.storeUnavailable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * JSON-container resolution mode: every reference is a key inside one shared AWS secret (a JSON
 * object of key-value pairs), fetched once per {@link #resolve}/{@link #list} call instead of one
 * AWS secret per reference.
 */
final class ContainerSecretResolver implements AwsSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ContainerSecretResolver.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final SecretsManagerClient client;
  private final String containerId;

  ContainerSecretResolver(
      final SecretsManagerClient client, final String pathPrefix, final String containerSecretId) {
    this.client = client;
    containerId = pathPrefix + containerSecretId;
  }

  @Override
  public Map<AwsSecretsManagerSecretReference, SecretResolutionResult> resolve(
      final Set<AwsSecretsManagerSecretReference> refs) {
    LOG.debug(
        "Resolving {} secret refs from AWS Secrets Manager container '{}'",
        refs.size(),
        containerId);
    final JsonNode json;
    try {
      final var raw = fetchRawSecret();
      if (raw == null) {
        return failAll(
            refs,
            INVALID_REF,
            "Container secret '" + containerId + "' has no string value " + "(binary secret)");
      }
      json = OBJECT_MAPPER.readTree(raw);
    } catch (final JsonProcessingException e) {
      return failAll(refs, INVALID_REF, "Container secret '" + containerId + "' is not valid JSON");
    } catch (final SecretsManagerException e) {
      final var code = classify(e);
      return failAll(refs, code, messageFor(code));
    } catch (final SdkClientException e) {
      throw storeUnavailable(e);
    }

    final Map<AwsSecretsManagerSecretReference, SecretResolutionResult> results =
        new LinkedHashMap<>(refs.size());
    for (final var ref : refs) {
      results.put(ref, extractKey(json, ref.name()));
    }
    return results;
  }

  @Override
  public Collection<AwsSecretsManagerSecretReference> list() {
    LOG.debug("Listing keys from AWS Secrets Manager container secret '{}'", containerId);
    try {
      final var raw = fetchRawSecret();
      if (raw == null) {
        return List.of();
      }
      final var json = OBJECT_MAPPER.readTree(raw);
      final var refs = new ArrayList<AwsSecretsManagerSecretReference>();
      json.fieldNames()
          .forEachRemaining(key -> refs.add(new AwsSecretsManagerSecretReference(key)));
      return refs;
    } catch (final JsonProcessingException e) {
      throw new SecretStoreUnavailableException(
          "Container secret '" + containerId + "' is not valid JSON: " + e.getMessage(), e);
    } catch (final SecretsManagerException | SdkClientException e) {
      throw storeUnavailable(e);
    }
  }

  /** Raw {@code secretString} of the container secret, or {@code null} for a binary secret. */
  private @Nullable String fetchRawSecret() {
    final var response =
        client.getSecretValue(GetSecretValueRequest.builder().secretId(containerId).build());
    return response.secretString();
  }

  private String messageFor(final SecretErrorCode code) {
    return switch (code) {
      case NOT_FOUND -> "Container secret not found: " + containerId;
      case INVALID_REF -> "Invalid container secret reference: " + containerId;
      case ACCESS_DENIED -> "Access denied for container secret: " + containerId;
    };
  }

  private SecretResolutionResult extractKey(final JsonNode container, final String key) {
    final var node = container.get(key);
    if (node == null || node.isNull()) {
      return new Failed(
          NOT_FOUND, "Key '" + key + "' not found in container secret '" + containerId + "'", null);
    }
    if (!node.isTextual()) {
      return new Failed(
          INVALID_REF,
          "Key '" + key + "' in container secret '" + containerId + "' is not a string value",
          null);
    }
    return new Resolved(node.asText());
  }

  private static Map<AwsSecretsManagerSecretReference, SecretResolutionResult> failAll(
      final Set<AwsSecretsManagerSecretReference> refs,
      final SecretErrorCode code,
      final String message) {
    final Map<AwsSecretsManagerSecretReference, SecretResolutionResult> results =
        new LinkedHashMap<>(refs.size());
    for (final var ref : refs) {
      results.put(ref, new Failed(code, message, null));
    }
    return results;
  }
}
