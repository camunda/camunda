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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchEngineClientUtils {

  private static final Logger LOG = LoggerFactory.getLogger(SearchEngineClientUtils.class);

  /**
   * Maximum length of a comma-delimited index-pattern string passed in a URL request. Requests
   * exceeding this limit may be rejected by the search engine.
   */
  public static final int MAX_INDEX_PATTERN_REQUEST_LENGTH = 4096;

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
    private final Map<String, Object> settingsBlock;
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
      settingsBlock = (Map<String, Object>) map.computeIfAbsent("settings", k -> new HashMap<>());
      indexBlock =
          (Map<String, Object>) settingsBlock.computeIfAbsent("index", k -> new HashMap<>());
    }

    public SchemaSettingsAppender withNumberOfShards(final int numberOfShards) {
      indexBlock.put("number_of_shards", String.valueOf(numberOfShards));
      return this;
    }

    public SchemaSettingsAppender withNumberOfReplicas(final int numberOfReplicas) {
      indexBlock.put("number_of_replicas", String.valueOf(numberOfReplicas));
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

    public boolean equalsSettings(final Map<String, Object> otherSettings) {
      return settingsBlock.equals(otherSettings);
    }
  }
}
