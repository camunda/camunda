/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static io.camunda.secretstore.azure.AzureKeyVaultErrors.classify;
import static io.camunda.secretstore.azure.AzureKeyVaultErrors.messageFor;
import static io.camunda.secretstore.azure.AzureKeyVaultErrors.storeUnavailable;

import com.azure.core.exception.HttpResponseException;
import com.azure.security.keyvault.secrets.SecretClient;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON-container resolution mode: every reference is a key inside one shared Key Vault secret (a
 * JSON object of key-value pairs), fetched once per {@link #resolve}/{@link #list} call instead of
 * one Key Vault secret per reference.
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
 *       io.camunda.secretstore.SecretErrorCode#NOT_FOUND}; {@link #list} does not return it.
 *   <li>The requested key is present with a JSON {@code null} value &mdash; {@link #resolve} fails
 *       only that reference with {@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND}, treating
 *       a null value as no value; {@link #list} <b>does</b> return it, because it reports the
 *       container's top-level field names regardless of their values.
 *   <li>A valid object whose value for a requested key is present but not a JSON string (number,
 *       object, array) &mdash; {@link #resolve} fails only that reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#INVALID_REF}; {@link #list} is unaffected.
 * </ul>
 */
final class ContainerSecretResolver implements AzureSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ContainerSecretResolver.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final SecretClient client;
  private final String containerName;

  ContainerSecretResolver(
      final SecretClient client, final String pathPrefix, final String containerSecretName) {
    this.client = client;
    containerName = pathPrefix + containerSecretName;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug(
        "Resolving {} secret refs from Azure Key Vault container '{}'",
        names.size(),
        containerName);
    final JsonNode json;
    try {
      json = OBJECT_MAPPER.readTree(fetchRawSecret());
    } catch (final JsonProcessingException e) {
      return failAll(
          names, INVALID_REF, "Container secret '" + containerName + "' is not valid JSON");
    } catch (final HttpResponseException e) {
      final var code = classify(e);
      return failAll(names, code, messageFor(code, "container secret", containerName));
    } catch (final RuntimeException e) {
      throw storeUnavailable(e);
    }
    if (!json.isObject()) {
      return failAll(
          names,
          INVALID_REF,
          "Container secret '"
              + containerName
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
    LOG.debug("Listing keys from Azure Key Vault container secret '{}'", containerName);
    final JsonNode json;
    try {
      json = OBJECT_MAPPER.readTree(fetchRawSecret());
    } catch (final JsonProcessingException e) {
      throw new SecretStoreUnavailableException(
          "Container secret '" + containerName + "' is not valid JSON");
    } catch (final RuntimeException e) {
      throw storeUnavailable(e);
    }
    // Kept outside the try so this deterministic malformed-content error is not caught by the broad
    // RuntimeException handler above and re-wrapped as a misleading "Azure is unavailable"
    // (transient) failure.
    if (!json.isObject()) {
      throw new SecretStoreUnavailableException(
          "Container secret '"
              + containerName
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
    // container mode reads exactly one secret at runtime, so getSecret on it is both the minimal
    // probe and the one that matches this mode's access footprint.
    fetchRawSecret();
  }

  private String fetchRawSecret() {
    return client.getSecret(containerName).getValue();
  }

  private SecretResolutionResult extractKey(final JsonNode container, final String key) {
    final var node = container.get(key);
    if (node == null || node.isNull()) {
      return new Failed(
          NOT_FOUND,
          "Key '" + key + "' not found in container secret '" + containerName + "'",
          null);
    }
    if (!node.isTextual()) {
      return new Failed(
          INVALID_REF,
          "Key '" + key + "' in container secret '" + containerName + "' is not a string value",
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
