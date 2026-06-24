/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SecondaryStorageSchemaConstraintsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String ES_TEMPLATE_DIR = "schema/elasticsearch/create/template";
  private static final String OS_TEMPLATE_DIR = "schema/opensearch/create/template";
  private static final String ES_INDEX_DIR = "schema/elasticsearch/create/index";
  private static final String OS_INDEX_DIR = "schema/opensearch/create/index";

  private static final List<String> ALL_SCHEMA_DIRS =
      List.of(ES_TEMPLATE_DIR, OS_TEMPLATE_DIR, ES_INDEX_DIR, OS_INDEX_DIR);

  // Low-risk types — freely usable without additional justification.
  private static final Set<String> STANDARD_TYPES =
      Set.of("keyword", "text", "long", "integer", "short", "date", "boolean", "object");

  // Complex types — carry significant performance trade-offs; require justification and Data Layer
  // sign-off to extend.
  // binary: stores Base64-encoded blobs in _source only (no inverted index). Large blobs inflate
  //         segment sizes and slow down reads; use only for true binary storage (e.g. resource
  // content).
  // join:   creates parent-child relationships with a hidden join field; expensive at query time.
  private static final Set<String> COMPLEX_TYPES = Set.of("binary", "join");

  // Fields whose names end with "id" or "key" are identifiers — must not use text.
  private static final Pattern IDENTIFIER_FIELD_PATTERN = Pattern.compile("(?i).*(id|key)$");

  private static final Set<String> ALL_ALLOWED_TYPES;

  static {
    final var all = new java.util.HashSet<>(STANDARD_TYPES);
    all.addAll(COMPLEX_TYPES);
    ALL_ALLOWED_TYPES = Set.copyOf(all);
  }

  @Test
  void shouldUseOnlyApprovedFieldTypes() throws Exception {
    final var violations = new ArrayList<String>();

    for (final var dir : ALL_SCHEMA_DIRS) {
      for (final var template : loadTemplates(dir)) {
        final var root = MAPPER.readTree(template.toFile());
        final var mappings = root.path("mappings");
        final var label = dirLabel(dir) + "/" + template.getFileName();
        walkProperties(mappings.path("properties"), label, "", violations);
      }
    }

    assertThat(violations)
        .as(
            """
            One or more ES/OS templates use field types that are not on the approved list.

            To add a new type:
              1. Update STANDARD_TYPES or COMPLEX_TYPES in SecondaryStorageSchemaConstraintsTest.java
              2. Add a comment explaining the use case and performance implications
              3. Get sign-off from the Data Layer team (#team-data-layer on Slack)
              4. Add @camunda/data-layer as a required reviewer on the PR

            See: docs/data-layer/working-with-secondary-storage.md
            """)
        .isEmpty();
  }

  @Test
  void shouldExplicitlySetDynamicToStrictEverywhere() throws Exception {
    final var violations = new ArrayList<String>();

    for (final var dir : ALL_SCHEMA_DIRS) {
      for (final var template : loadTemplates(dir)) {
        final var label = dirLabel(dir) + "/" + template.getFileName();
        final var root = MAPPER.readTree(template.toFile());
        final var mappings = root.path("mappings");

        // Dynamic must be explicitly present at the mappings root — absence defaults to true in
        // ES/OS.
        final var dynamicNode = mappings.path("dynamic");
        if (dynamicNode.isMissingNode()) {
          violations.add("[%s] mappings root is missing \"dynamic\": \"strict\"".formatted(label));
        } else if (!"strict".equals(dynamicNode.asText())) {
          violations.add(
              "[%s] mappings root has \"dynamic\": \"%s\" — only \"strict\" is permitted"
                  .formatted(label, dynamicNode.asText()));
        }

        // Any explicit "dynamic" override inside nested objects must also be "strict".
        checkNestedDynamic(mappings.path("properties"), label, "", violations);
      }
    }

    assertThat(violations)
        .as(
            """
            One or more ES/OS templates are missing or overriding "dynamic": "strict".

            Every template must set "dynamic": "strict" at the mappings root. Omitting it
            defaults to dynamic mapping (true), which allows arbitrary fields to be indexed
            without schema review. Nested object fields that override "dynamic" must also
            set it to "strict".

            To change this setting, get sign-off from the Data Layer team (#team-data-layer).
            See: docs/data-layer/working-with-secondary-storage.md
            """)
        .isEmpty();
  }

  @Test
  void shouldHaveSameFilenamesBetweenElasticsearchAndOpensearch() throws Exception {
    for (final var pair :
        List.of(
            new String[] {ES_TEMPLATE_DIR, OS_TEMPLATE_DIR},
            new String[] {ES_INDEX_DIR, OS_INDEX_DIR})) {
      final var esNames =
          loadTemplates(pair[0]).stream()
              .map(p -> p.getFileName().toString())
              .collect(Collectors.toSet());
      final var osNames =
          loadTemplates(pair[1]).stream()
              .map(p -> p.getFileName().toString())
              .collect(Collectors.toSet());

      assertThat(esNames)
          .as(
              """
              Elasticsearch and OpenSearch schema directories must contain the same files (%s vs %s).
              A file present in one directory but not the other indicates an incomplete schema change.
              Every schema change must be applied to both directories.
              Note: this check verifies file names only, not that the file contents are identical.
              """
                  .formatted(pair[0], pair[1]))
          .isEqualTo(osNames);
    }
  }

  @Test
  void shouldHaveIdenticalMappingsBetweenElasticsearchAndOpensearch() throws Exception {
    final var violations = new ArrayList<String>();

    for (final var pair :
        List.of(
            new String[] {ES_TEMPLATE_DIR, OS_TEMPLATE_DIR},
            new String[] {ES_INDEX_DIR, OS_INDEX_DIR})) {
      final Map<String, Path> esFiles =
          loadTemplates(pair[0]).stream()
              .collect(Collectors.toMap(p -> p.getFileName().toString(), p -> p));
      final Map<String, Path> osFiles =
          loadTemplates(pair[1]).stream()
              .collect(Collectors.toMap(p -> p.getFileName().toString(), p -> p));

      for (final var entry : esFiles.entrySet()) {
        final var osFile = osFiles.get(entry.getKey());
        if (osFile == null) {
          continue; // covered by filename-symmetry test
        }
        final var esMappings = MAPPER.readTree(entry.getValue().toFile()).path("mappings");
        final var osMappings = MAPPER.readTree(osFile.toFile()).path("mappings");
        if (!esMappings.equals(osMappings)) {
          violations.add(
              "%s/%s vs %s/%s"
                  .formatted(dirLabel(pair[0]), entry.getKey(), dirLabel(pair[1]), entry.getKey()));
        }
      }
    }

    assertThat(violations)
        .as(
            """
            The following files have diverging mappings between Elasticsearch and OpenSearch.
            Both backends must use identical field mappings to avoid silent type mismatches.
            Every schema change must be applied to both directories.
            """)
        .isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private List<Path> loadTemplates(final String classpathDir)
      throws URISyntaxException, IOException {
    final URL dirUrl =
        SecondaryStorageSchemaConstraintsTest.class.getClassLoader().getResource(classpathDir);
    assertThat(dirUrl).as("Classpath directory not found: %s".formatted(classpathDir)).isNotNull();
    try (final var stream = Files.walk(Path.of(dirUrl.toURI()), 1)) {
      return stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
    }
  }

  private static String dirLabel(final String dir) {
    final var engine = dir.contains("elasticsearch") ? "ES" : "OS";
    final var kind = dir.endsWith("/index") ? "index" : "template";
    return engine + "/" + kind;
  }

  /**
   * Recursively walks a {@code "properties"} node, collecting violations for any field whose {@code
   * "type"} is not in the approved set. Also recurses into {@code object} and {@code nested}
   * sub-properties and multi-field {@code "fields"} blocks.
   */
  private void walkProperties(
      final JsonNode propertiesNode,
      final String fileName,
      final String parentPath,
      final List<String> violations) {
    if (propertiesNode == null || propertiesNode.isMissingNode()) {
      return;
    }

    for (final Entry<String, JsonNode> entry : propertiesNode.properties()) {
      final String fieldName = entry.getKey();
      final JsonNode fieldDef = entry.getValue();
      final String fieldPath = parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;

      final JsonNode typeNode = fieldDef.path("type");
      if (!typeNode.isMissingNode()) {
        final String type = typeNode.asText();
        if (!ALL_ALLOWED_TYPES.contains(type)) {
          violations.add(
              "[%s] Field '%s' uses disallowed type '%s'".formatted(fileName, fieldPath, type));
        }
        if ("text".equals(type) && IDENTIFIER_FIELD_PATTERN.matcher(fieldName).matches()) {
          violations.add(
              "[%s] Field '%s' uses type 'text' but its name indicates an identifier or key — use 'keyword' instead"
                  .formatted(fileName, fieldPath));
        }
      }

      // Recurse into sub-properties of object and nested fields.
      walkProperties(fieldDef.path("properties"), fileName, fieldPath, violations);

      // Recurse into multi-field "fields" blocks (e.g. keyword + text sub-fields).
      walkProperties(fieldDef.path("fields"), fileName, fieldPath + ".fields", violations);
    }
  }

  /**
   * Checks that no nested {@code "properties"} block overrides {@code "dynamic"} to a non-strict
   * value. The root-level check is done separately in the main test method.
   */
  private void checkNestedDynamic(
      final JsonNode propertiesNode,
      final String fileName,
      final String parentPath,
      final List<String> violations) {
    if (propertiesNode == null || propertiesNode.isMissingNode()) {
      return;
    }

    for (final Entry<String, JsonNode> entry : propertiesNode.properties()) {
      final String fieldName = entry.getKey();
      final JsonNode fieldDef = entry.getValue();
      final String fieldPath = parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;

      final JsonNode dynamicNode = fieldDef.path("dynamic");
      if (!dynamicNode.isMissingNode() && !"strict".equals(dynamicNode.asText())) {
        violations.add(
            "[%s] Field '%s' overrides \"dynamic\" to \"%s\" — only \"strict\" is permitted"
                .formatted(fileName, fieldPath, dynamicNode.asText()));
      }

      checkNestedDynamic(fieldDef.path("properties"), fileName, fieldPath, violations);
    }
  }
}
