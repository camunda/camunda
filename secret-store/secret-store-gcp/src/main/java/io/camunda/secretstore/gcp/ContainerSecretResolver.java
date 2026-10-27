/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static io.camunda.secretstore.gcp.GcpSecretManagerErrors.classify;
import static io.camunda.secretstore.gcp.GcpSecretManagerErrors.messageFor;
import static io.camunda.secretstore.gcp.GcpSecretManagerErrors.storeUnavailable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON-container resolution mode: every reference is a key inside one shared GCP secret (a JSON
 * object of key-value pairs), fetched once per {@link #resolve}/{@link #list} call instead of one
 * GCP secret per reference.
 *
 * <p>Expected format: the container secret's value must be a flat JSON <b>object</b> whose
 * top-level values are all JSON strings, e.g. {@code {"db-password": "s3cr3t", "api-token":
 * "tok3n"}}. Nested objects/arrays are not traversed. Deviations are handled as follows:
 *
 * <ul>
 *   <li>Not valid JSON at all &mdash; {@link #resolve} fails every requested reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#INVALID_REF}; {@link #list} throws {@link
 *       io.camunda.secretstore.SecretStoreUnavailableException}.
 *   <li>Valid JSON but not an object (e.g. an array or a bare string/number) &mdash; same as above;
 *       the container secret is unusable regardless of which method is called.
 *   <li>The requested key is absent &mdash; {@link #resolve} fails only that reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#NOT_FOUND}; {@link #list} does not return it, since
 *       an absent key is not one of the container's top-level field names.
 *   <li>The requested key is present with a JSON {@code null} value &mdash; {@link #resolve} fails
 *       only that reference with {@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND}, treating
 *       a null value as no value; {@link #list} <b>does</b> return it, because it reports the
 *       container's top-level field names regardless of their values (just as it lists a key whose
 *       value is a non-string).
 *   <li>A valid object whose value for a requested key is present but not a JSON string (number,
 *       object, array) &mdash; {@link #resolve} fails only that reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#INVALID_REF}; {@link #list} is unaffected, since it
 *       only inspects field names, not values.
 * </ul>
 */
final class ContainerSecretResolver implements GcpSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ContainerSecretResolver.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String LATEST_VERSION = "latest";

  private final SecretManagerServiceClient client;
  private final @Nullable String projectId;
  private final String containerId;

  ContainerSecretResolver(
      final SecretManagerServiceClient client,
      final @Nullable String projectId,
      final String pathPrefix,
      final String containerSecretId) {
    this.client = client;
    this.projectId = projectId;
    containerId = pathPrefix + containerSecretId;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug(
        "Resolving {} secret refs from GCP Secret Manager container '{}'",
        names.size(),
        containerId);
    final JsonNode json;
    try {
      json = OBJECT_MAPPER.readTree(fetchRawSecret());
    } catch (final JsonProcessingException e) {
      return failAll(
          names, INVALID_REF, "Container secret '" + containerId + "' is not valid JSON");
    } catch (final ApiException e) {
      final var code = classify(e);
      return failAll(names, code, messageFor(code, "container secret", containerId));
    } catch (final RuntimeException e) {
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
    LOG.debug("Listing keys from GCP Secret Manager container secret '{}'", containerId);
    final JsonNode json;
    try {
      json = OBJECT_MAPPER.readTree(fetchRawSecret());
    } catch (final JsonProcessingException e) {
      throw new SecretStoreUnavailableException(
          "Container secret '" + containerId + "' is not valid JSON");
    } catch (final RuntimeException e) {
      throw storeUnavailable(e);
    }
    // Kept outside the try so this deterministic malformed-content error is not caught by the broad
    // RuntimeException handler above and re-wrapped as a misleading "GCP is unavailable"
    // (transient)
    // failure.
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
  }

  @Override
  public void validateConnectivity() {
    // container mode reads exactly one secret at runtime, so accessSecretVersion on it is both the
    // minimal probe and the one that matches this mode's IAM footprint.
    fetchRawSecret();
  }

  private String fetchRawSecret() {
    final var projectId = Objects.requireNonNull(this.projectId, "projectId must be non-null");
    final var response =
        client.accessSecretVersion(SecretVersionName.of(projectId, containerId, LATEST_VERSION));
    return response.getPayload().getData().toStringUtf8();
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
