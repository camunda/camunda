/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Drift guard (Tier 1) for the {@code x-required-permissions} OpenAPI vendor extension
 * (camunda/camunda#54727, ADR docs/adr/security/001-endpoint-required-permission-mapping.md).
 *
 * <p>{@code resource-permissions.json} is the registry the Spectral {@code
 * verify-required-permissions} rule reads to validate that every declared {@code (resourceType,
 * permissionType)} pair is one the resource type actually supports. That registry is only
 * trustworthy if it stays in sync with the source of truth — the {@link AuthorizationResourceType}
 * enum. This test asserts the JSON equals {@link
 * AuthorizationResourceType#buildResourcePermissionsMap()} so the two can never silently drift.
 */
final class ResourcePermissionsRegistryTest {

  private static final String REGISTRY_RELATIVE_PATH =
      "zeebe/gateway-protocol/src/main/proto/v2/resource-permissions.json";

  @Test
  void registryMustMirrorAuthorizationResourceTypeEnum() throws IOException {
    // given
    final Path registryFile = locateRegistry();
    final Map<String, List<String>> declared = readDeclaredRegistry(registryFile);

    // when
    final Map<String, List<String>> expected = normalise(buildExpectedRegistry());

    // then
    assertThat(declared)
        .as(
            "%s must mirror AuthorizationResourceType.buildResourcePermissionsMap(). "
                + "Regenerate it from the enum (do not hand-edit) whenever a resource type or "
                + "permission changes.",
            REGISTRY_RELATIVE_PATH)
        .isEqualTo(expected);
  }

  private static Map<String, List<String>> buildExpectedRegistry() {
    return AuthorizationResourceType.buildResourcePermissionsMap();
  }

  private static Map<String, List<String>> readDeclaredRegistry(final Path registryFile)
      throws IOException {
    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode root = mapper.readTree(Files.readAllBytes(registryFile));
    final JsonNode permissions = root.get("resourcePermissions");
    assertThat(permissions)
        .as("resource-permissions.json must contain a 'resourcePermissions' object")
        .isNotNull();

    final Map<String, List<String>> result = new TreeMap<>();
    final Iterator<Entry<String, JsonNode>> fields = permissions.fields();
    while (fields.hasNext()) {
      final Entry<String, JsonNode> field = fields.next();
      final List<String> values = new ArrayList<>();
      field.getValue().forEach(node -> values.add(node.asText()));
      values.sort(String::compareTo);
      result.put(field.getKey(), values);
    }
    return result;
  }

  private static Map<String, List<String>> normalise(final Map<String, List<String>> map) {
    final Map<String, List<String>> result = new TreeMap<>();
    map.forEach(
        (key, values) -> {
          final List<String> sorted = new ArrayList<>(values);
          sorted.sort(String::compareTo);
          result.put(key, sorted);
        });
    return result;
  }

  private static Path locateRegistry() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      final Path candidate = current.resolve(REGISTRY_RELATIVE_PATH);
      if (Files.exists(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    throw new IllegalStateException(
        "Could not locate " + REGISTRY_RELATIVE_PATH + " by walking up from the working directory");
  }
}
