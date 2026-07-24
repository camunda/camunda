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
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.messageFor;
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
 *
 * <p>Expected format: the container secret's string value must be a flat JSON <b>object</b> whose
 * top-level values are all JSON strings, e.g. {@code {"db-password": "s3cr3t", "api-token":
 * "tok3n"}}. Nested objects/arrays are not traversed. Deviations are handled as follows:
 *
 * <ul>
 *   <li>Not valid JSON at all &mdash; {@link #resolve} fails every requested reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#INVALID_REF}; {@link #list} throws {@link
 *       io.camunda.secretstore.SecretStoreUnavailableException}.
 *   <li>Valid JSON but not an object (e.g. an array or a bare string/number) &mdash; same as above;
 *       the container secret is unusable regardless of which method is called.
 *   <li>The requested key is absent, or present with a JSON {@code null} value &mdash; {@link
 *       #resolve} fails only that reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#NOT_FOUND}; {@link #list} is unaffected, since a
 *       missing/null key simply isn't in the field names it returns.
 *   <li>A valid object whose value for a requested key is present but not a JSON string (number,
 *       object, array) &mdash; {@link #resolve} fails only that reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#INVALID_REF}; {@link #list} is unaffected, since it
 *       only inspects field names, not values.
 * </ul>
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
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug(
        "Resolving {} secret refs from AWS Secrets Manager container '{}'",
        names.size(),
        containerId);
    final JsonNode json;
    try {
      final var raw = fetchRawSecret();
      if (raw == null) {
        return failAll(
            names,
            INVALID_REF,
            "Container secret '" + containerId + "' has no string value (binary secret)");
      }
      json = OBJECT_MAPPER.readTree(raw);
    } catch (final JsonProcessingException e) {
      return failAll(
          names, INVALID_REF, "Container secret '" + containerId + "' is not valid JSON");
    } catch (final SecretsManagerException e) {
      final var code = classify(e);
      return failAll(names, code, messageFor(code, "container secret", containerId));
    } catch (final SdkClientException e) {
      throw storeUnavailable(e);
    }
    if (!json.isObject()) {
      return failAll(
          names,
          INVALID_REF,
          "Container secret '"
              + containerId
              + "' is not a JSON object (was "
              + json.getNodeType()
              + ")");
    }

    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      results.put(name, extractKey(json, name));
    }
    return results;
  }

  @Override
  public List<String> list() {
    LOG.debug("Listing keys from AWS Secrets Manager container secret '{}'", containerId);
    try {
      final var raw = fetchRawSecret();
      if (raw == null) {
        return List.of();
      }
      final var json = OBJECT_MAPPER.readTree(raw);
      if (!json.isObject()) {
        throw new SecretStoreUnavailableException(
            "Container secret '"
                + containerId
                + "' is not a JSON object (was "
                + json.getNodeType()
                + ")");
      }
      final var names = new ArrayList<String>();
      json.fieldNames().forEachRemaining(names::add);
      return names;
    } catch (final JsonProcessingException e) {
      throw new SecretStoreUnavailableException(
          "Container secret '" + containerId + "' is not valid JSON");
    } catch (final SecretsManagerException | SdkClientException e) {
      throw storeUnavailable(e);
    }
  }

  /**
   * Raw {@code secretString} of the container secret. AWS returns exactly one of {@code
   * secretString}/{@code secretBinary} per secret; {@code null} here means the value lives in
   * {@code secretBinary} instead, which this store doesn't support resolving.
   */
  private @Nullable String fetchRawSecret() {
    final var response =
        client.getSecretValue(GetSecretValueRequest.builder().secretId(containerId).build());
    return response.secretString();
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

  private static Map<String, SecretResolutionResult> failAll(
      final Set<String> names, final SecretErrorCode code, final String message) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      results.put(name, new Failed(code, message, null));
    }
    return results;
  }
}
