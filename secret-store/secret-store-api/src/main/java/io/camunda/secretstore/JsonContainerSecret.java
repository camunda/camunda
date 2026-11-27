/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads references from a container secret: one secret whose value is a flat JSON <b>object</b>
 * whose top-level values are JSON strings, e.g. {@code {"db-password": "s3cr3t", "api-token":
 * "tok3n"}}. Nested objects and arrays are not traversed. Shared by the stores' container modes, so
 * they all apply the same rules:
 *
 * <ul>
 *   <li>Not valid JSON, or valid JSON but not an object &mdash; {@link #resolve} fails every
 *       requested reference with {@link SecretErrorCode#INVALID_REF}; {@link #fieldNames} throws
 *       {@link SecretStoreUnavailableException}.
 *   <li>The requested key is absent, or present with a JSON {@code null} value &mdash; {@link
 *       #resolve} fails only that reference with {@link SecretErrorCode#NOT_FOUND}.
 *   <li>The requested key's value is present but not a JSON string (number, boolean, object, array)
 *       &mdash; {@link #resolve} fails only that reference with {@link
 *       SecretErrorCode#INVALID_REF}.
 * </ul>
 *
 * <p>A message names the container secret, never its content: a parse error's own message can quote
 * the content, so it is dropped.
 */
public final class JsonContainerSecret {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private JsonContainerSecret() {}

  /**
   * @param containerName the container secret's name, for the messages
   * @param json the container secret's value
   * @return one result per requested name
   */
  public static Map<String, SecretResolutionResult> resolve(
      final String containerName, final String json, final Set<String> names) {
    final JsonNode container;
    try {
      container = OBJECT_MAPPER.readTree(json);
    } catch (final JsonProcessingException e) {
      return failAll(
          names, INVALID_REF, "Container secret '" + containerName + "' is not valid JSON");
    }
    if (!container.isObject()) {
      return failAll(names, INVALID_REF, notAnObject(containerName, container));
    }
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      results.put(name, extractKey(containerName, container, name));
    }
    return results;
  }

  /**
   * @param containerName the container secret's name, for the messages
   * @param json the container secret's value
   * @return the container's top-level field names, whatever their values
   * @throws SecretStoreUnavailableException if {@code json} is not a JSON object
   */
  public static List<String> fieldNames(final String containerName, final String json) {
    final JsonNode container;
    try {
      container = OBJECT_MAPPER.readTree(json);
    } catch (final JsonProcessingException e) {
      throw new SecretStoreUnavailableException(
          "Container secret '" + containerName + "' is not valid JSON");
    }
    if (!container.isObject()) {
      throw new SecretStoreUnavailableException(notAnObject(containerName, container));
    }
    final var names = new ArrayList<String>();
    container.fieldNames().forEachRemaining(names::add);
    return names;
  }

  /** Fails every name in {@code names} with the same code and message. */
  public static Map<String, SecretResolutionResult> failAll(
      final Set<String> names, final SecretErrorCode code, final String message) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      results.put(name, new Failed(code, message, null));
    }
    return results;
  }

  private static SecretResolutionResult extractKey(
      final String containerName, final JsonNode container, final String key) {
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

  private static String notAnObject(final String containerName, final JsonNode container) {
    return "Container secret '"
        + containerName
        + "' is not a JSON object (was "
        + container.getNodeType()
        + ")";
  }
}
