/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards {@link IndexTemplateDescriptor#hasCustomSettings()} against drifting from the schema files
 * it describes. Every template's per-restart settings-update path (see {@code
 * SearchEngineClientUtils.SchemaSettingsAppender#matchesConfiguredTemplate}) only ever
 * compares/writes the settings keys runtime configuration owns (shards/replicas/refresh_interval),
 * regardless of this flag — the search engine normalizes the rest of a settings block (e.g.
 * injecting {@code "type": "custom"}, coercing scalars to lists) in a way that can never be trusted
 * to match a byte-for-byte diff of the raw JSON, which is what caused #63764's rewrite storm in the
 * first place. This flag instead only decides whether {@code
 * SchemaManager#forceCustomSettingsTemplates} gives the template's JSON-owned settings a full,
 * unconditional rewrite on a schema-version change. A template that actually owns such settings but
 * is wrongly marked {@code false} silently drops a real settings change a version bump was supposed
 * to apply; one wrongly marked {@code true} costs an unnecessary rewrite on every version change.
 */
class IndexTemplateCustomSettingsTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @ParameterizedTest(name = "{0}")
  @MethodSource("templateDescriptors")
  void shouldHaveNoSettingsBlockUnlessDeclaringCustomSettings(
      final IndexTemplateDescriptor descriptor) throws IOException {
    final var settings = readSettingsBlock(descriptor);
    if (descriptor.hasCustomSettings()) {
      assertThat(settings)
          .as(
              "%s declares hasCustomSettings() but its schema file '%s' has no settings block",
              descriptor.getTemplateName(), descriptor.getMappingsClasspathFilename())
          .isNotNull();
    } else {
      assertThat(settings)
          .as(
              "%s does not declare hasCustomSettings() but its schema file '%s' has a settings "
                  + "block; either mark it hasCustomSettings(), or remove that settings block if "
                  + "it only duplicates what runtime configuration already applies",
              descriptor.getTemplateName(), descriptor.getMappingsClasspathFilename())
          .isNull();
    }
  }

  /**
   * A settings block, once it exists, must place everything under {@code index} — the shape the
   * search engine actually stores and the shape {@code SchemaSettingsAppender} appends
   * shards/replicas/refresh_interval into. A sibling key (e.g. a stray top-level {@code analysis})
   * is silently relocated by the search engine on write, so it looks harmless until a before/after
   * comparison of that block is attempted.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("templateDescriptors")
  void shouldOnlyPlaceSettingsUnderTheIndexKey(final IndexTemplateDescriptor descriptor)
      throws IOException {
    final var settings = readSettingsBlock(descriptor);
    if (settings != null) {
      assertThat(settings.keySet())
          .as(
              "%s's schema file '%s' has settings outside of settings.index",
              descriptor.getTemplateName(), descriptor.getMappingsClasspathFilename())
          .containsExactly("index");
    }
  }

  @Test
  void listViewAndIncidentShouldDeclareCustomSettings() {
    assertThat(new ListViewTemplate(null, true).hasCustomSettings()).isTrue();
    assertThat(new IncidentTemplate(null, true).hasCustomSettings()).isTrue();
  }

  private static Stream<IndexTemplateDescriptor> templateDescriptors() {
    return Stream.of(true, false)
        .flatMap(
            isElasticsearch -> new IndexDescriptors(null, isElasticsearch).templates().stream());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readSettingsBlock(final IndexTemplateDescriptor descriptor)
      throws IOException {
    try (final var stream =
        IndexTemplateCustomSettingsTest.class.getResourceAsStream(
            descriptor.getMappingsClasspathFilename())) {
      final var json = OBJECT_MAPPER.readValue(stream, new TypeReference<Map<String, Object>>() {});
      return (Map<String, Object>) json.get("settings");
    }
  }
}
