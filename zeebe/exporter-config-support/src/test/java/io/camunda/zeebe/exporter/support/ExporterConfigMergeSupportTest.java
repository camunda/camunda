/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ExporterConfigMergeSupportTest {

  @Test
  void shouldCollapseRelaxedPropertyKeySpellingsBeforeMerging() {
    // given — root and tenant spell the same property differently
    final Map<String, Object> rootArgs = Map.of("index-prefix", "root");
    final Map<String, Object> tenantArgs = Map.of("indexPrefix", "tenant");

    // when
    final var merged = ExporterConfigMergeSupport.merge(SampleConfig.class, rootArgs, tenantArgs);

    // then — both collapse to the canonical key, tenant wins
    assertThat(merged).containsOnly(Map.entry("indexprefix", "tenant"));
  }

  @Test
  void shouldRecurseIntoNestedPojoProperties() {
    // given — a nested POJO property partially overridden by the tenant
    final Map<String, Object> rootArgs =
        Map.of("nested", Map.of("size", 1000, "delay", 5), "url", "http://root:9200");
    final Map<String, Object> tenantArgs = Map.of("nested", Map.of("delay", 9));

    // when
    final var merged = ExporterConfigMergeSupport.merge(SampleConfig.class, rootArgs, tenantArgs);

    // then — the untouched nested sibling and the untouched top-level property survive
    assertThat(merged)
        .containsEntry("url", "http://root:9200")
        .containsEntry("nested", Map.of("size", 1000, "delay", 9));
  }

  @Test
  void shouldReplaceMapTypedPropertiesWholesale() {
    // given — a Map-typed property whose keys are user data
    final Map<String, Object> rootArgs =
        Map.of("userData", Map.of("Root-Key", "a", "shared", "root"));
    final Map<String, Object> tenantArgs = Map.of("userData", Map.of("Tenant-Key", "b"));

    // when
    final var merged = ExporterConfigMergeSupport.merge(SampleConfig.class, rootArgs, tenantArgs);

    // then — no per-key merge inside the user-data map, and its keys are not normalized
    assertThat(merged).containsEntry("userdata", Map.of("Tenant-Key", "b"));
  }

  @Test
  void shouldPreserveMapTypedPropertyKeysWhenNormalizing() {
    // given — only the root sets the user-data map
    final Map<String, Object> rootArgs =
        Map.of("user-data", Map.of("Kebab-Cased-User-Key", "kept"));

    // when
    final var merged = ExporterConfigMergeSupport.merge(SampleConfig.class, rootArgs, Map.of());

    // then — the property key normalizes, the user-data keys inside do not
    assertThat(merged).containsEntry("userdata", Map.of("Kebab-Cased-User-Key", "kept"));
  }

  @Test
  void shouldReplaceScalarsAndLists() {
    // given
    final Map<String, Object> rootArgs = Map.of("url", "http://root:9200", "hosts", List.of("a"));
    final Map<String, Object> tenantArgs =
        Map.of("url", "http://tenant:9200", "hosts", List.of("b", "c"));

    // when
    final var merged = ExporterConfigMergeSupport.merge(SampleConfig.class, rootArgs, tenantArgs);

    // then — lists are not concatenated, scalars are not kept
    assertThat(merged)
        .containsEntry("url", "http://tenant:9200")
        .containsEntry("hosts", List.of("b", "c"));
  }

  @Test
  void shouldReplaceUnknownKeysWithoutRecursion() {
    // given — keys that match no property of the config class (cannot be introspected)
    final Map<String, Object> rootArgs = Map.of("unknown", Map.of("a", 1, "b", 2));
    final Map<String, Object> tenantArgs = Map.of("unknown", Map.of("b", 99));

    // when
    final var merged = ExporterConfigMergeSupport.merge(SampleConfig.class, rootArgs, tenantArgs);

    // then — replaced wholesale, no guessing about a model we cannot see
    assertThat(merged).containsEntry("unknown", Map.of("b", 99));
  }

  @Test
  void shouldKeepRootEntriesTheTenantDoesNotTouch() {
    // given
    final Map<String, Object> rootArgs = Map.of("url", "http://root:9200", "indexPrefix", "root");
    final Map<String, Object> tenantArgs = Map.of("indexPrefix", "tenant");

    // when
    final var merged = ExporterConfigMergeSupport.merge(SampleConfig.class, rootArgs, tenantArgs);

    // then
    assertThat(merged)
        .containsOnly(Map.entry("url", "http://root:9200"), Map.entry("indexprefix", "tenant"));
  }

  @Test
  void shouldNotMutateItsInputs() {
    // given
    final Map<String, Object> rootArgs = new LinkedHashMap<>(Map.of("nested", nested(1, 5)));
    final Map<String, Object> tenantArgs = new LinkedHashMap<>(Map.of("nested", Map.of("size", 2)));

    // when
    ExporterConfigMergeSupport.merge(SampleConfig.class, rootArgs, tenantArgs);

    // then
    assertThat(rootArgs).containsEntry("nested", nested(1, 5));
    assertThat(tenantArgs).containsEntry("nested", Map.of("size", 2));
  }

  private static Map<String, Object> nested(final int size, final int delay) {
    return Map.of("size", size, "delay", delay);
  }

  /** Minimal stand-in for an exporter configuration class. */
  @SuppressWarnings("unused")
  public static final class SampleConfig {
    public String url;
    public String indexPrefix;
    public List<String> hosts;
    public NestedConfig nested = new NestedConfig();
    public Map<String, String> userData;

    public static final class NestedConfig {
      public int size;
      public int delay;
    }
  }
}
