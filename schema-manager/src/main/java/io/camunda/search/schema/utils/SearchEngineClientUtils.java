/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchEngineClientUtils {

  /**
   * Maximum length of a comma-delimited index-pattern string passed in a URL request. Requests
   * exceeding this limit may be rejected by the search engine. ES/OS limit this to 4096, but we use
   * a lower value to be conservative
   */
  public static final int MAX_INDEX_PATTERN_REQUEST_LENGTH = 3500;

  /**
   * Index-level settings keys that runtime configuration owns and can therefore be safely diffed
   * against the search engine's normalized rendering. Any other key (e.g. {@code analysis}) is
   * owned by the template JSON, not runtime config, and comparing it against the engine's
   * normalized form is unreliable (key relocation, injected defaults, scalar/list coercion), so it
   * is deliberately excluded from {@link SchemaSettingsAppender#equalsManagedSettings(Map)}.
   */
  private static final Set<String> MANAGED_INDEX_SETTINGS_KEYS =
      Set.of("number_of_shards", "number_of_replicas", "refresh_interval");

  private static final Logger LOG = LoggerFactory.getLogger(SearchEngineClientUtils.class);
  private final ObjectMapper objectMapper;

  public SearchEngineClientUtils(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String listIndicesByAlias(final List<IndexDescriptor> indexDescriptors) {
    return indexDescriptors.stream()
        .map(IndexDescriptor::getAlias)
        .collect(Collectors.joining(","));
  }

  public static <T, U> U convertValue(final T fromValue, final Function<T, U> converter) {
    return fromValue != null ? converter.apply(fromValue) : null;
  }

  /**
   * Splits a comma-separated index-pattern string into batches where each batch's length does not
   * exceed {@link #MAX_INDEX_PATTERN_REQUEST_LENGTH}. A single pattern that is itself longer than
   * the limit is placed in its own batch.
   *
   * <p><b>Note:</b> If the pattern contains exclusion entries (segments starting with {@code -},
   * e.g. {@code "index-a*,-index-a"}), batching is skipped entirely because splitting at a batch
   * boundary could separate an include pattern from its paired exclusion, producing incorrect query
   * results. In that case a warning is logged and the original pattern is returned as-is.
   *
   * @param namePattern comma-separated index patterns, e.g. {@code "index-a*,index-b*"}
   * @return ordered list of comma-joined batches
   */
  public static List<String> batchPatterns(final String namePattern) {
    if (namePattern == null || namePattern.isEmpty()) {
      return Collections.emptyList();
    }
    final String[] patterns = namePattern.split(",");
    for (final String pattern : patterns) {
      if (pattern.startsWith("-")) {
        LOG.debug(
            "Index pattern [{}] contains exclusion entries; skipping batching to preserve "
                + "exclusion semantics. The full pattern will be sent in a single request.",
            namePattern);
        return Collections.singletonList(namePattern);
      }
    }
    final List<String> batches = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    for (final String pattern : patterns) {
      if (current.length() > 0
          && current.length() + 1 + pattern.length() > MAX_INDEX_PATTERN_REQUEST_LENGTH) {
        batches.add(current.toString());
        current.setLength(0);
      }
      if (current.length() > 0) {
        current.append(",");
      }
      current.append(pattern);
    }
    if (current.length() > 0) {
      batches.add(current.toString());
    }
    return batches;
  }

  public <T> T mapToSettings(
      final Map<String, String> settingsMap, final Function<InputStream, T> settingsDeserializer) {
    try (final var settingsStream =
        IOUtils.toInputStream(
            objectMapper.writeValueAsString(settingsMap), StandardCharsets.UTF_8)) {

      return settingsDeserializer.apply(settingsStream);
    } catch (final IOException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to serialise settings in PutSettingsRequest [%s]", settingsMap.toString()),
          e);
    }
  }

  public class SchemaSettingsAppender {
    private final Map<String, Object> map;
    private final Map<String, Object> indexBlock;

    /**
     * Reads the settings block in {@code file} and writes additional settings using a builder
     * syntax.
     *
     * @param file whose settings block will be read and appended.
     * @throws IOException if unable to parse the given file into a map.
     */
    public SchemaSettingsAppender(final InputStream file) throws IOException {
      map = objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {});
      final var settingsBlock =
          (Map<String, Object>) map.computeIfAbsent("settings", k -> new HashMap<>());
      indexBlock =
          (Map<String, Object>) settingsBlock.computeIfAbsent("index", k -> new HashMap<>());
    }

    public SchemaSettingsAppender withNumberOfShards(final Object numberOfShards) {
      indexBlock.put("number_of_shards", numberOfShards);
      return this;
    }

    public SchemaSettingsAppender withNumberOfReplicas(final Object numberOfReplicas) {
      indexBlock.put("number_of_replicas", numberOfReplicas);
      return this;
    }

    public SchemaSettingsAppender withRefreshInterval(final String refreshInterval) {
      if (refreshInterval != null) {
        indexBlock.put("refresh_interval", refreshInterval);
      }
      return this;
    }

    public InputStream build() throws IOException {
      return new ByteArrayInputStream(objectMapper.writeValueAsBytes(map));
    }

    /**
     * Compares only the index-level settings that runtime configuration owns ({@link
     * #MANAGED_INDEX_SETTINGS_KEYS}), ignoring everything else in the settings block (e.g. an
     * {@code analysis} block owned by the template JSON, which the search engine normalizes on
     * storage — injecting defaults, relocating keys, coercing scalars to lists — in a way a raw
     * comparison could never reliably match). A template whose JSON-owned settings may have changed
     * is instead rewritten unconditionally on a schema-version change; see {@code
     * SchemaManager#forceCustomSettingsTemplates}.
     *
     * <p>Values are compared with {@link Objects#equals} and are deliberately not coerced, so a
     * caller has to append the managed settings in the same representation the engine it talks to
     * renders them back in — Elasticsearch returns them as strings, OpenSearch as numbers, which is
     * why the two call sites differ. Getting that wrong makes this permanently return {@code false}
     * and turns every restart into a redundant template write for every template — the failure this
     * comparison exists to prevent.
     *
     * @param otherSettings the stored settings block, or {@code null} when the template has none. A
     *     template that stores no settings at all cannot match a configured one, so it counts as a
     *     mismatch and gets repaired rather than failing the comparison.
     */
    @SuppressWarnings("unchecked")
    private boolean equalsManagedSettings(final Map<String, Object> otherSettings) {
      if (otherSettings == null) {
        return false;
      }
      final var otherIndexBlock =
          (Map<String, Object>) otherSettings.getOrDefault("index", Map.of());
      return MANAGED_INDEX_SETTINGS_KEYS.stream()
          .allMatch(key -> Objects.equals(indexBlock.get(key), otherIndexBlock.get(key)));
    }

    /**
     * Decides whether an existing index template's stored settings already match this configured
     * settings, i.e. whether the caller can skip writing them. Only ever compares the settings
     * runtime configuration owns; see {@link #equalsManagedSettings(Map)}.
     *
     * @param currentPriority the template's currently stored priority.
     * @param configuredPriority the priority runtime configuration wants.
     * @param currentSettings the template's currently stored settings block, serialized to a map,
     *     or {@code null} when the template stores none.
     */
    public boolean matchesConfiguredTemplate(
        final Long currentPriority,
        final Long configuredPriority,
        final Map<String, Object> currentSettings) {
      return Objects.equals(configuredPriority, currentPriority)
          && equalsManagedSettings(currentSettings);
    }
  }
}
